package dev.tvlauncher.power

import dev.tvlauncher.power.WakeTimeline.Event
import dev.tvlauncher.power.WakeTimeline.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeTimelineTest {
    private val own = "dev.tvlauncher"

    @Test fun lastInputResumedAfterWakeIsNotNavigation() {
        val r = WakeTimeline.analyze(listOf(
            Event(1, Kind.RESUMED, "com.tcl.tv"),
            Event(2, Kind.SCREEN_OFF),
            Event(3, Kind.SCREEN_ON),
            Event(4, Kind.RESUMED, "com.tcl.tv")
        ), own)
        assertEquals("com.tcl.tv", r.foreground)
        assertEquals("com.tcl.tv", r.beforeSleep)
        assertEquals(false, r.navigatedAfterWake)
    }

    @Test fun appLeftFromBeforeStandbyIsNotNavigation() {
        val r = WakeTimeline.analyze(listOf(
            Event(1, Kind.RESUMED, "com.netflix.ninja"),
            Event(2, Kind.SCREEN_OFF),
            Event(3, Kind.SCREEN_ON),
            Event(4, Kind.RESUMED, "com.netflix.ninja")
        ), own)
        assertEquals(false, r.navigatedAfterWake)
    }

    @Test fun appOpenedAfterWakeIsNavigation() {
        val r = WakeTimeline.analyze(listOf(
            Event(1, Kind.RESUMED, "com.tcl.tv"),
            Event(2, Kind.SCREEN_OFF),
            Event(3, Kind.SCREEN_ON),
            Event(4, Kind.RESUMED, "com.tcl.tv"),
            Event(5, Kind.RESUMED, "com.google.android.youtube.tv")
        ), own)
        assertEquals("com.google.android.youtube.tv", r.foreground)
        assertEquals(true, r.navigatedAfterWake)
    }

    @Test fun tvSourceAppearingAfterWakeIsNotNavigation() {
        val r = WakeTimeline.analyze(listOf(
            Event(1, Kind.RESUMED, own),
            Event(2, Kind.SCREEN_OFF),
            Event(3, Kind.SCREEN_ON),
            Event(4, Kind.RESUMED, own),
            Event(5, Kind.RESUMED, "com.tcl.tv")
        ), own)
        assertEquals(false, r.navigatedAfterWake)
        assertEquals("com.tcl.tv", r.foreground)
    }

    @Test fun noWakeSeenIsUnknown() {
        val r = WakeTimeline.analyze(listOf(Event(1, Kind.RESUMED, "com.tcl.tv")), own)
        assertEquals("com.tcl.tv", r.foreground)
        assertNull(r.navigatedAfterWake)
    }

    @Test fun emptyIsUnknown() {
        val r = WakeTimeline.analyze(emptyList(), own)
        assertNull(r.foreground)
        assertNull(r.navigatedAfterWake)
    }

    @Test fun unsortedInputIsSorted() {
        val r = WakeTimeline.analyze(listOf(
            Event(4, Kind.RESUMED, "com.tcl.tv"),
            Event(3, Kind.SCREEN_ON),
            Event(2, Kind.SCREEN_OFF),
            Event(1, Kind.RESUMED, "com.tcl.tv")
        ), own)
        assertEquals(false, r.navigatedAfterWake)
    }
}
