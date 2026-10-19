package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test

class SoundEngineTest {
    class FakeTrack : PcmTrack {
        var released = false; var playing = false; var starts = 0
        var failWrite = false; var throwWrite = false
        override fun write(data: ShortArray): Boolean { if (throwWrite) throw IllegalStateException(); return !failWrite }
        override fun start(volume: Float) { playing = true; starts++ }
        override fun isPlaying() = playing
        override fun release() { released = true }
    }

    class Harness(var settings: SoundSettings = SoundSettings()) {
        val tracks = ArrayList<FakeTrack>()
        var failWrite = false; var throwWrite = false
        var sig = "a"; var onLoad: (() -> Unit)? = null
        var now = 10_000L
        val tasks = ArrayList<() -> Unit>()
        val delayed = ArrayList<() -> Unit>()
        val engine = SoundEngine(
            object : TrackFactory {
                override fun create(sampleRate: Int, sizeBytes: Int) =
                    FakeTrack().also { it.failWrite = failWrite; it.throwWrite = throwWrite; tracks += it }
            },
            object : PcmSource {
                override fun load(event: SoundEvent, s: SoundSettings): Pcm? { onLoad?.invoke(); return Pcm(ShortArray(100) { 1 }, 22050) }
                override fun customSignature(event: SoundEvent) = sig
            },
            { settings }, 1 shl 20, { now }, { tasks += it }, { _, b -> delayed += b }
        )
        fun run() { while (tasks.isNotEmpty()) tasks.removeAt(0)() }
    }

    @Test fun build_afterRelease_releasesTrack() {
        val h = Harness()
        h.onLoad = { h.engine.release() }   // release lands while a build is in flight
        h.engine.prewarm(); h.run()
        assertTrue(h.tracks.isNotEmpty())
        assertTrue(h.tracks.all { it.released })
    }

    @Test fun release_releasesRetiringTracks() {
        val h = Harness()
        h.engine.prewarm(); h.run()
        val first = h.tracks.toList()
        first.forEach { it.playing = true }
        h.settings = h.settings.copy(navThemeId = "soft")
        h.engine.prewarm(); h.run()
        assertTrue("playing old tracks are deferred", first.any { !it.released })
        h.engine.release()
        assertTrue(h.tracks.all { it.released })
        h.delayed.forEach { it() }   // late delayed task after release is harmless
    }

    @Test fun retiring_track_released_by_delayed_task() {
        val h = Harness()
        h.engine.prewarm(); h.run()
        val first = h.tracks.toList(); first.forEach { it.playing = true }
        h.settings = h.settings.copy(navThemeId = "soft")
        h.engine.prewarm(); h.run()
        h.delayed.forEach { it() }
        assertTrue(first.count { it.released } >= 4)
    }

    @Test fun writeFailure_releasesTrack() {
        for (thr in listOf(false, true)) {
            val h = Harness()
            h.failWrite = !thr; h.throwWrite = thr
            h.engine.prewarm(); h.run()
            val n = h.tracks.size
            assertTrue(n > 0); assertTrue(h.tracks.all { it.released })
            h.engine.prewarm(); h.run()   // failures are cached: no rebuild
            assertEquals(n, h.tracks.size)
        }
    }

    @Test fun changedFile_rebuildsWithinThrottle() {
        val h = Harness(SoundSettings(useCustomFolder = true))
        h.engine.prewarm(); h.run()
        val base = h.tracks.size
        h.sig = "b"
        h.engine.play(SoundEvent.SELECT); h.run()
        assertEquals(base + 1, h.tracks.size)
        h.sig = "c"; h.now += 500
        h.engine.play(SoundEvent.SELECT); h.run()
        assertEquals("throttled", base + 1, h.tracks.size)
        h.now += 1600
        h.engine.play(SoundEvent.SELECT); h.run()
        assertEquals(base + 2, h.tracks.size)
    }

    @Test fun play_miss_is_skipped_then_built() {
        val h = Harness()
        h.engine.play(SoundEvent.SELECT)
        assertTrue(h.tracks.isEmpty())
        h.run()
        assertEquals(1, h.tracks.size)
        h.engine.play(SoundEvent.SELECT)
        assertEquals(1, h.tracks[0].starts)
    }

    @Test fun staleQueuedBuild_dropped() {
        val h = Harness()
        h.engine.play(SoundEvent.SELECT)
        h.settings = h.settings.copy(navThemeId = "soft")
        h.run()
        assertTrue(h.tracks.isEmpty())
    }
}
