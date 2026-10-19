package dev.tvlauncher.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModelTest {
    private val min = 60_000L

    @Test fun timedFocus_endsByItself() {
        val s = FocusModel.start(0, 30)
        assertTrue(s.isOn(29 * min))
        assertEquals(1, s.minutesLeft(29 * min + 1))
        assertFalse(s.isOn(30 * min))
        assertEquals(FocusState(), FocusModel.normalize(s, 31 * min))
        assertTrue(FocusModel.quiet(s, 0).sounds)
        assertFalse(FocusModel.quiet(s, 31 * min).ambient)
    }

    @Test fun untilOff_staysOn() {
        val s = FocusModel.start(0, null)
        assertTrue(s.isOn(Long.MAX_VALUE - 1))
        assertNull(s.minutesLeft(5))
        assertEquals("On until you turn it off", FocusModel.status(s, 5))
    }

    @Test fun toggle_andLabels() {
        val on = FocusModel.toggle(FocusState(), 0, 60)
        assertTrue(on.isOn(1))
        assertFalse(FocusModel.toggle(on, 1, 60).isOn(1))
        assertEquals("On · 1 h left", FocusModel.status(on, 0))
        assertEquals("On · 1 h 30 min left", FocusModel.status(FocusModel.start(0, 120), 30 * min))
        assertEquals("Off", FocusModel.status(FocusState(), 0))
        assertEquals(listOf("30 minutes", "1 hour", "2 hours", "Until I turn it off"), FocusModel.LENGTHS.map { FocusModel.lengthLabel(it) })
    }
}
