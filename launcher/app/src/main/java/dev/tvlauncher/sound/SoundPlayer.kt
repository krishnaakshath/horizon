package dev.tvlauncher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import java.io.File

/**
 * Plays short UI sounds through cached static AudioTracks.
 * Never requests audio focus, so playing video/music is not interrupted.
 * [play] never throws and never blocks.
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
    private class Entry(val signature: String, val track: AudioTrack, val bytes: Int)

    private val cache = HashMap<SoundEvent, Entry>()
    private var cachedBytes = 0
    private val moveLimiter = RateLimiter(MOVE_MIN_INTERVAL_MS) { SystemClock.elapsedRealtime() }
    private val budget = if (lite) LITE_BUDGET_BYTES else FULL_BUDGET_BYTES

    /** App-specific folder for custom sounds (no storage permission needed). */
    fun customDir(): File? = try { context.getExternalFilesDir("sounds") } catch (e: Exception) { null }

    fun play(event: SoundEvent) {
        try {
            val s = settings()
            if (!s.enabled || !s.isEventOn(event) || s.volume <= 0) return
            if (event == SoundEvent.MOVE && !moveLimiter.tryAcquire()) return
            val entry = obtain(event, s) ?: return
            val t = entry.track
            val v = s.volume / 100f * HEADROOM
            t.setVolume(v)
            t.stop()
            t.setPlaybackHeadPosition(0)
            t.play()
        } catch (e: Exception) {
            // never throw from UI sound
        }
    }

    /** Plays regardless of the master/per-event toggles (for settings previews). */
    fun preview(event: SoundEvent) {
        val s = settings()
        val forced = s.copy(enabled = true, perEvent = s.perEvent + (event to true),
            volume = if (s.volume <= 0) 50 else s.volume)
        try {
            val entry = obtain(event, forced) ?: return
            entry.track.setVolume(forced.volume / 100f * HEADROOM)
            entry.track.stop()
            entry.track.setPlaybackHeadPosition(0)
            entry.track.play()
        } catch (e: Exception) {
        }
    }

    fun release() {
        try {
            cache.values.forEach { runCatching { it.track.release() } }
        } finally {
            cache.clear()
            cachedBytes = 0
        }
    }

    private fun obtain(event: SoundEvent, s: SoundSettings): Entry? {
        val sig = "${s.themeId}|${s.useCustomFolder}"
        cache[event]?.let { if (it.signature == sig) return it else drop(event) }
        var rate = SAMPLE_RATE
        var pcm: ShortArray? = null
        if (s.useCustomFolder) {
            val f = File(customDir() ?: File("/nonexistent"), "${event.name.lowercase()}.wav")
            if (f.isFile && f.length() <= WavParser.MAX_BYTES) {
                WavParser.parse(f.readBytes())?.let { rate = it.sampleRate; pcm = it.samples }
            }
        }
        val data = pcm ?: ToneSynth.render(SoundThemes.byId(s.themeId).events.getValue(event), SAMPLE_RATE)
        if (data.isEmpty()) return null
        val bytes = data.size * 2
        if (bytes > budget) return null
        if (cachedBytes + bytes > budget) {
            // Over budget: evict everything else (tracks rebuild lazily).
            cache.keys.toList().forEach { drop(it) }
        }
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
        if (track.write(data, 0, data.size) <= 0) {
            track.release()
            return null
        }
        return Entry(sig, track, bytes).also { cache[event] = it; cachedBytes += bytes }
    }

    private fun drop(event: SoundEvent) {
        cache.remove(event)?.let {
            runCatching { it.track.release() }
            cachedBytes -= it.bytes
        }
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
    fun release() = player.release()
}
