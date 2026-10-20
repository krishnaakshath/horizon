package dev.tvlauncher.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTriggersTest {
    @Test
    fun releaseBuild_ignoresEverything() {
        for (a in listOf(DebugTriggers.TRIP, DebugTriggers.RESET, DebugTriggers.CRASH_HOME)) {
            assertNull(DebugTriggers.decide(a, debuggable = false, count = 3))
        }
    }

    @Test
    fun trip_defaultsToThree_andClampsCount() {
        assertEquals(DebugTriggers.Command.Trip(3), DebugTriggers.decide(DebugTriggers.TRIP, true, null))
        assertEquals(DebugTriggers.Command.Trip(1), DebugTriggers.decide(DebugTriggers.TRIP, true, 0))
        assertEquals(DebugTriggers.Command.Trip(1), DebugTriggers.decide(DebugTriggers.TRIP, true, -5))
        assertEquals(DebugTriggers.Command.Trip(10), DebugTriggers.decide(DebugTriggers.TRIP, true, 99))
        assertEquals(DebugTriggers.Command.Trip(5), DebugTriggers.decide(DebugTriggers.TRIP, true, 5))
    }

    @Test
    fun openSettings_needsAPlainSectionId() {
        val open = DebugTriggers.OPEN_SETTINGS
        assertTrue(DebugTriggers.knows(open))
        assertEquals(DebugTriggers.Command.OpenSettings("home_layout"),
            DebugTriggers.decide(open, true, null, DebugTriggers.parseSection("home_layout")))
        assertNull(DebugTriggers.decide(open, false, null, "home_layout"))
        assertNull(DebugTriggers.decide(open, true, null, null))
        for (bad in listOf("", "Home", "home layout", "../x", "a;b", 5, "x".repeat(41)))
            assertNull(bad.toString(), DebugTriggers.parseSection(bad))
    }

    @Test
    fun resetAndCrash() {
        assertEquals(DebugTriggers.Command.Reset, DebugTriggers.decide(DebugTriggers.RESET, true, null))
        assertEquals(DebugTriggers.Command.CrashHome, DebugTriggers.decide(DebugTriggers.CRASH_HOME, true, 7))
    }

    @Test
    fun readCount_malformedOrMissing_isNull() {
        assertNull(DebugTriggers.parseCount(null))
        assertNull(DebugTriggers.parseCount("3"))
        assertNull(DebugTriggers.parseCount(3L))
        assertEquals(4, DebugTriggers.parseCount(4))
    }

    @Test
    fun otherActions_ignored() {
        assertNull(DebugTriggers.decide(null, true, null))
        assertNull(DebugTriggers.decide("android.intent.action.MAIN", true, null))
        assertNull(DebugTriggers.decide("dev.tvlauncher.action.SPOTLIGHT", true, null))
        assertEquals("dev.tvlauncher.debug.TRIP_SAFE_MODE", DebugTriggers.TRIP)
    }
}
