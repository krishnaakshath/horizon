package dev.tvlauncher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import java.io.File
import java.util.concurrent.Executors

/**
 * Plays short UI sounds through cached static AudioTracks.
 * Never requests audio focus, so playing video/music is not interrupted.
 *
 * Threading contract: [play], [preview], [prewarm] and [release] may be called from any thread
 * and never throw. [play] never synthesises, reads files or builds tracks on the caller thread:
 * if an event's track is not ready yet that single play is skipped and the build is queued on
 * the player's own background thread. The [settings] lambda is also invoked from that
 * background thread, so it must be thread-safe (e.g. read a @Volatile field).
 * The cache and byte counter are guarded by [lock]; failures are cached per (event, signature)
 * so a broken event is not rebuilt until its signature (theme, custom flag, wav mtime/size) changes.
 */
class SoundPlayer(
    private val context: Context,
    private val settings: () -> SoundSettings,
    private val lite: Boolean = try {
        DeviceProfileReader.read(context).effectsMode == EffectsMode.LITE
    } catch (e: Exception) {
        false
    }
) {
    /** [track] == null is a cached negative result (failed, silent or over budget). */
    private class Entry(val key: String, val sig: String, val track: AudioTrack?, val bytes: Int)

    private val lock = Any()
    private val cache = HashMap<SoundEvent, Entry>()
    private val pending = HashSet<SoundEvent>()
    private var cachedBytes = 0
    private val moveLimiter = RateLimiter(MOVE_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }
    private val budget = if (lite) LITE_BUDGET_BYTES else FULL_BUDGET_BYTES
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ui-sound").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    /** App-specific folder for custom sounds (no storage permission needed). */
    fun customDir(): File? = try { context.getExternalFilesDir("sounds") } catch (e: Exception) { null }

    /** Builds all event tracks for the current settings on the background thread. */
    fun prewarm() = submit {
        val s = settings()
        for (e in SoundEvent.values()) build(e, s)
    }

    fun play(event: SoundEvent) {
        try {
            val s = settings()
            if (!s.enabled || !s.isEventOn(event) || s.volume <= 0) return
            if (event == SoundEvent.MOVE && !moveLimiter.tryAcquire()) return
            val key = keyOf(event, s)
            val started = synchronized(lock) {
                val e = cache[event]
                if (e == null || e.key != key) {
                    if (pending.add(event)) submit { synchronized(lock) { pending.remove(event) }; build(event, s) }
                    false
                } else {
                    e.track?.let { startTrack(it, s.volume) }
                    true
                }
            }
            if (!started) return
        } catch (e: Exception) {
            // never throw from UI sound
        }
    }

    /** Plays regardless of master/per-event toggles (settings previews). Builds if needed, off-thread. */
    fun preview(event: SoundEvent) = submit {
        val s = settings()
        val forced = s.copy(
            enabled = true,
            perEvent = s.perEvent + (event to true),
            volume = if (s.volume <= 0) 50 else s.volume
        )
        build(event, forced)
        synchronized(lock) { cache[event]?.track?.let { startTrack(it, forced.volume) } }
    }

    fun release() {
        try {
            executor.shutdownNow()
        } catch (e: Exception) {
        }
        synchronized(lock) {
            cache.values.forEach { it.track?.let { t -> runCatching { t.release() } } }
            cache.clear()
            pending.clear()
            cachedBytes = 0
        }
    }

    private fun submit(block: () -> Unit) {
        try {
            executor.execute { try { block() } catch (e: Throwable) { } }
        } catch (e: Exception) {
            // executor shut down
        }
    }

    private fun startTrack(t: AudioTrack, volume: Int) {
        try {
            t.setVolume(volume / 100f * HEADROOM)
            t.stop()
            t.setPlaybackHeadPosition(0)
            t.play()
        } catch (e: Exception) {
        }
    }

    private fun keyOf(event: SoundEvent, s: SoundSettings) = "${s.themeId}|${s.useCustomFolder}"

    private fun customFile(event: SoundEvent): File =
        File(customDir() ?: File("/nonexistent"), "${event.name.lowercase()}.wav")

    /** Background only: may stat files, synthesise, read wav and create the AudioTrack. */
    private fun build(event: SoundEvent, s: SoundSettings) {
        val key = keyOf(event, s)
        val file = if (s.useCustomFolder) customFile(event) else null
        val sig = if (file != null) "$key|${file.lastModified()}:${file.length()}" else key
        synchronized(lock) { if (cache[event]?.sig == sig) return }

        var entry = Entry(key, sig, null, 0)
        try {
            var rate = SAMPLE_RATE
            var pcm: ShortArray? = null
            if (file != null && file.isFile && file.length() <= WavParser.MAX_BYTES) {
                WavParser.parse(file.readBytes())?.let { rate = it.sampleRate; pcm = it.samples }
            }
            val spec = SoundThemes.byId(s.themeId).events.getValue(event)
            val data = pcm ?: if (spec.gain > 0f) ToneSynth.render(spec, SAMPLE_RATE) else ShortArray(0)
            val bytes = data.size * 2
            if (bytes > 0 && bytes <= budget) {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(bytes)
                    .build()
                if (track.write(data, 0, data.size) > 0) entry = Entry(key, sig, track, bytes)
                else track.release()
            }
        } catch (e: Exception) {
            // keep negative entry
        }

        synchronized(lock) {
            val old = cache.remove(event)
            if (old != null) { cachedBytes -= old.bytes; retire(old) }
            if (entry.track != null && cachedBytes + entry.bytes > budget) {
                // Over budget: evict other idle entries (never one that is playing).
                for (k in cache.keys.toList()) {
                    if (cachedBytes + entry.bytes <= budget) break
                    val o = cache[k] ?: continue
                    if (o.track != null && isPlaying(o.track)) continue
                    cache.remove(k); cachedBytes -= o.bytes; retire(o)
                }
                if (cachedBytes + entry.bytes > budget) {
                    retire(entry)
                    entry = Entry(key, sig, null, 0)
                }
            }
            cache[event] = entry
            cachedBytes += entry.bytes
        }
    }

    private fun isPlaying(t: AudioTrack) = try {
        t.playState == AudioTrack.PLAYSTATE_PLAYING
    } catch (e: Exception) {
        false
    }

    /** Releases a track, deferring if it is still sounding. Call with [lock] held or on its own entry. */
    private fun retire(e: Entry) {
        val t = e.track ?: return
        if (isPlaying(t)) {
            try {
                executor.schedule({ runCatching { t.release() } }, 600, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (x: Exception) {
                runCatching { t.release() }
            }
        } else runCatching { t.release() }
    }

    companion object {
        const val SAMPLE_RATE = 22050
        const val HEADROOM = 0.6f
        const val MOVE_MIN_INTERVAL_MS = 45L
        const val LITE_BUDGET_BYTES = 512 * 1024
        const val FULL_BUDGET_BYTES = 2 * 1024 * 1024
    }
}

/**
 * Call-site facade for later wiring (not wired yet). Intended call sites:
 * - [prewarm]: once from HomeActivity.onCreate/onResume (and after settings change) so first sounds are ready.
 * - [onFocusMoved]: HomeActivity/ShelfAdapter/TopBar/Dock focus-change listener when focus lands on a new item
 *   (the player rate-limits to one per 45 ms, so calling per focus change is fine).
 * - [onSelect]: OK/click on a tile, dock button, top-bar button or TileMenu entry that does not launch.
 * - [onBack]: BACK press that closes TileMenu/panel or leaves a sub-screen.
 * - [onLaunch]: right before startActivity of an app (HomeActivity launch path / LaunchOverlay start).
 * - [onMenuOpen]: TileMenu / SoundSettingsPanel / settings overlay shown (long-press OK or MENU key).
 * - [onError]: launch failure (app not found / startActivity exception) or rejected action.
 */
class UiSoundsFacade(private val player: SoundPlayer) {
    fun onFocusMoved() = player.play(SoundEvent.MOVE)
    fun onSelect() = player.play(SoundEvent.SELECT)
    fun onBack() = player.play(SoundEvent.BACK)
    fun onLaunch() = player.play(SoundEvent.LAUNCH)
    fun onMenuOpen() = player.play(SoundEvent.MENU_OPEN)
    fun onError() = player.play(SoundEvent.ERROR)
    fun prewarm() = player.prewarm()
    fun release() = player.release()
}
