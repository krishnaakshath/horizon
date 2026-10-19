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
}
