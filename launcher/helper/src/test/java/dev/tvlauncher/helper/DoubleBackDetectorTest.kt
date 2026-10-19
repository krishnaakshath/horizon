package dev.tvlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBackDetectorTest {
    private var now = 1000L
    private fun detector(window: Long = 500) = DoubleBackDetector(window) { now }

    @Test fun single_press_false() {
        assertFalse(detector().onBackUp())
    }

    @Test fun two_within_window_true() {
        val d = detector()
        assertFalse(d.onBackUp())
        now += 300
        assertTrue(d.onBackUp())
    }

    @Test fun two_outside_window_false() {
        val d = detector()
        assertFalse(d.onBackUp())
        now += 501
        assertFalse(d.onBackUp())
    }

    @Test fun third_press_after_double_starts_new_window() {
        val d = detector()
        d.onBackUp()
        now += 100
        assertTrue(d.onBackUp())
        now += 100
        assertFalse(d.onBackUp())
        now += 100
        assertTrue(d.onBackUp())
    }

    @Test fun window_clamped_300_900() {
        assertEquals(300L, DoubleBackDetector.clampWindow(10))
        assertEquals(900L, DoubleBackDetector.clampWindow(5000))
        assertEquals(500L, DoubleBackDetector.clampWindow(500))
        val d = detector(10)
        d.onBackUp()
        now += 299
        assertTrue(d.onBackUp())
    }

    @Test fun isArmed_after_first_press_only_within_window() {
        val d = detector()
        assertFalse(d.isArmed())
        d.onBackUp()
        assertTrue(d.isArmed())
        now += 501
        assertFalse(d.isArmed())
    }

    @Test fun boundary_exactlyWindow_inclusive_true() {
        val d = detector(500)
        d.onBackUp()
        now += 500
        assertTrue(d.onBackUp())
    }

    @Test fun boundary_windowPlusOne_false() {
        val d = detector(500)
        d.onBackUp()
        now += 501
        assertFalse(d.onBackUp())
    }

    @Test fun configured100_behavesAs300() {
        val d = detector(100)
        d.onBackUp(); now += 300
        assertTrue(d.onBackUp())
        d.onBackUp(); now += 301
        assertFalse(d.onBackUp())
    }

    @Test fun configured5000_behavesAs900() {
        val d = detector(5000)
        d.onBackUp(); now += 900
        assertTrue(d.onBackUp())
        d.onBackUp(); now += 901
        assertFalse(d.onBackUp())
    }
}
