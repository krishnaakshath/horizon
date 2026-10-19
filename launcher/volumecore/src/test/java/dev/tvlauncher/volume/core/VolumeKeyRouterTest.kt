package dev.tvlauncher.volume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyRouterTest {
    private val UP = 24
    private val DOWN = 25
    private val MUTE = 164
    private val BACK = 4

    private var enabled = true
    private val backend = FakeBackend("a")
    private val controller = VolumeController(listOf(backend)) { "r" }
    private val shown = ArrayList<VolumeState>()
    private var releases = 0
    private val seen = ArrayList<Pair<VolumeKey, VolumeResult?>>()
    private val router = VolumeKeyRouter({ enabled }, controller, { shown += it }, { releases++ }, { k, r -> seen += k to r })

    @Test fun other_keys_pass_untouched() {
        assertFalse(router.onKey(BACK, true, 0, false))
        assertFalse(router.onKey(BACK, false, 0, false))
        assertTrue(seen.isEmpty())
    }

    @Test fun taken_press_consumes_down_repeats_and_up() {
        assertTrue(router.onKey(UP, true, 0, false))
        assertTrue(router.onKey(UP, true, 1, false))
        assertTrue(router.onKey(UP, true, 2, false))
        assertTrue(router.onKey(UP, false, 0, false))
        assertEquals(13, backend.state!!.level)
        assertEquals(3, shown.size)
        assertEquals(1, releases)
        assertEquals(1, seen.size) // only first presses are counted
    }

    @Test fun kill_switch_off_passes_everything() {
        enabled = false
        assertFalse(router.onKey(UP, true, 0, false))
        assertFalse(router.onKey(UP, true, 1, false))
        assertFalse(router.onKey(UP, false, 0, false))
        assertEquals(0, backend.writes)
        assertEquals(VolumeKey.UP to null, seen.single())
    }

    @Test fun passed_press_passes_its_repeats_and_up() {
        backend.fixed = true
        assertFalse(router.onKey(DOWN, true, 0, false))
        backend.fixed = false
        assertFalse(router.onKey(DOWN, true, 1, false)) // the system owns this press
        assertFalse(router.onKey(DOWN, false, 0, false))
        assertEquals(0, backend.writes)
    }

    @Test fun repeat_that_cannot_be_handled_passes_but_up_stays_paired() {
        assertTrue(router.onKey(UP, true, 0, false))
        backend.fixed = true
        assertFalse(router.onKey(UP, true, 1, false))
        assertTrue(router.onKey(UP, false, 0, false)) // the DOWN was ours
    }

    @Test fun mute_toggles_on_first_press_only() {
        assertTrue(router.onKey(MUTE, true, 0, false))
        assertTrue(router.onKey(MUTE, true, 1, false))
        assertTrue(router.onKey(MUTE, false, 0, false))
        assertTrue(backend.state!!.muted)
        assertTrue(router.onKey(MUTE, true, 0, false))
        assertFalse(backend.state!!.muted)
    }

    @Test fun canceled_up_is_consumed_without_restarting_the_timer() {
        assertTrue(router.onKey(UP, true, 0, false))
        assertTrue(router.onKey(UP, false, 0, true))
        assertEquals(0, releases)
    }

    @Test fun stray_up_passes() {
        assertFalse(router.onKey(UP, false, 0, false))
    }

    @Test fun errors_fail_open() {
        val bad = VolumeKeyRouter({ throw IllegalStateException() }, controller, {})
        assertFalse(bad.onKey(UP, true, 0, false))
        assertFalse(bad.onKey(UP, false, 0, false))
    }

    @Test fun hide_timer() {
        val t = HideTimer()
        assertEquals(1500L, t.durationMs)
        assertFalse(t.armed)
        assertFalse(t.due(10_000))
        t.poke(1000)
        assertFalse(t.due(2499))
        assertEquals(1L, t.remaining(2499))
        assertTrue(t.due(2500))
        t.poke(2000) // a new key extends it
        assertFalse(t.due(2500))
        assertTrue(t.due(3500))
        t.cancel()
        assertFalse(t.due(9999))
        assertEquals(0L, t.remaining(9999))
    }

    @Test fun bar_motion() {
        assertEquals(BarMotion.OFF, BarMotion.resolve(false, 4_000_000, 0f, null))
        assertEquals(BarMotion.OFF, BarMotion.resolve(false, 4_000_000, 1f, "off"))
        assertEquals(BarMotion.LITE, BarMotion.resolve(false, 1_000_000, 1f, null))
        assertEquals(BarMotion.LITE, BarMotion.resolve(true, 4_000_000, 1f, "full"))
        assertEquals(BarMotion.LITE, BarMotion.resolve(false, 4_000_000, 1f, "lite"))
        assertEquals(BarMotion.FULL, BarMotion.resolve(false, 4_000_000, 1f, "full"))
        assertEquals(BarMotion.FULL, BarMotion.resolve(false, 0, 1f, null)) // unknown RAM: device decides nothing
        assertEquals(0L, BarMotion.OFF.showMs)
        assertFalse(BarMotion.LITE.shadow)
        assertTrue(BarMotion.FULL.slide)
    }
}
