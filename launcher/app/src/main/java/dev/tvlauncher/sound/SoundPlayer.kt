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
import java.util.concurrent.TimeUnit

/**
 * Plays short UI sounds through cached static AudioTracks. Never requests audio focus, so playing
 * video/music is not interrupted.
 *
 * Threading contract (see [SoundEngine]): all methods are callable from any thread and never throw;
 * [play] never synthesises or touches files on the caller thread, a not-yet-built sound is skipped
 * once and built on the player's background thread. The [settings] supplier is invoked from that
 * background thread too: back it with a @Volatile field or AtomicReference.
 * Wiring should still call [prewarm] in onResume and after the settings panel's onChanged.
 */
class SoundPlayer(
    private val context: Context,
    settings: () -> SoundSettings,
    lite: Boolean = try {
        DeviceProfileReader.read(context).effectsMode == EffectsMode.LITE
    } catch (e: Exception) {
        false
    }
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ui-sound").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    private val engine = SoundEngine(
        factory = AudioTrackFactory(),
        source = AndroidPcmSource(),
        settings = settings,
        budget = if (lite) LITE_BUDGET_BYTES else FULL_BUDGET_BYTES,
        clock = { SystemClock.elapsedRealtime() },
        submit = { block -> try { executor.execute(block) } catch (e: Exception) { } },
        scheduleRelease = { ms, block ->
            try { executor.schedule(block, ms, TimeUnit.MILLISECONDS) } catch (e: Exception) { }
        }
    )

    /** App-specific folder for custom sounds (no storage permission needed). */
    fun customDir(): File? = try { context.getExternalFilesDir("sounds") } catch (e: Exception) { null }

    fun prewarm() = engine.prewarm()
    fun play(event: SoundEvent) = engine.play(event)
    fun preview(event: SoundEvent) = engine.preview(event)

    fun release() {
        engine.release()   // synchronously frees cached and retiring tracks, blocks later stores
        try { executor.shutdownNow() } catch (e: Exception) { }
    }

    private class AudioPcmTrack(private val t: AudioTrack) : PcmTrack {
        override fun write(data: ShortArray) = t.write(data, 0, data.size) > 0
        override fun start(volume: Float) {
            try {
                t.setVolume(volume); t.stop(); t.setPlaybackHeadPosition(0); t.play()
            } catch (e: Exception) {
            }
        }
        override fun isPlaying() = t.playState == AudioTrack.PLAYSTATE_PLAYING
        override fun release() = t.release()
    }

    private class AudioTrackFactory : TrackFactory {
        override fun create(sampleRate: Int, sizeBytes: Int): PcmTrack = AudioPcmTrack(
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(sizeBytes)
                .build()
        )
    }

    private inner class AndroidPcmSource : PcmSource {
        private fun file(event: SoundEvent) =
            File(customDir() ?: File("/nonexistent"), "${event.name.lowercase()}.wav")

        override fun customSignature(event: SoundEvent): String {
            val f = file(event)
            return "${f.lastModified()}:${f.length()}"
        }

        override fun load(event: SoundEvent, s: SoundSettings): Pcm? {
            if (s.useCustomFolder) {
                val f = file(event)
                if (f.isFile && f.length() <= WavParser.MAX_BYTES) {
                    WavParser.parse(f.readBytes())?.let { return Pcm(it.samples, it.sampleRate) }
                }
            }
            // Calibrated spec is computed lazily here, on the builder thread only.
            val spec = SoundThemes.spec(s.themeIdFor(event), event)
            if (spec.gain <= 0f) return null
            return Pcm(ToneSynth.render(spec, SAMPLE_RATE), SAMPLE_RATE)
        }
    }

    companion object {
        const val SAMPLE_RATE = 22050
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
