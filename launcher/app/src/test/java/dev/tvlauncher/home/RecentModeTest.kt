package dev.tvlauncher.home

import dev.tvlauncher.insights.AppMode
import dev.tvlauncher.insights.ModeClassifier
import dev.tvlauncher.insights.UsageEvent
import dev.tvlauncher.insights.UsageEvent.Type.PAUSED
import dev.tvlauncher.insights.UsageEvent.Type.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentModeTest {
    private val now = 10L * 3_600_000
    private val min = 60_000L
    private val classify = { pkg: String -> ModeClassifier.classify(pkg, null) }
    private fun ev(pkg: String, type: UsageEvent.Type, at: Long) = UsageEvent(pkg, type, at)

    @Test
    fun videoLeftTwoMinutesAgo_isVideo() {
        val events = listOf(
            ev("com.netflix.ninja", RESUMED, now - 50 * min),
            ev("com.netflix.ninja", PAUSED, now - 2 * min),
            ev("dev.tvlauncher", RESUMED, now - 2 * min)
        )
        assertEquals(AppMode.VIDEO, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun leftFourMinutesAgo_isOther() {
        val events = listOf(
            ev("com.spotify.tv.android", RESUMED, now - 30 * min),
            ev("com.spotify.tv.android", PAUSED, now - 4 * min),
            ev("dev.tvlauncher", RESUMED, now - 4 * min)
        )
        assertEquals(AppMode.OTHER, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun exactlyThreeMinutes_counts() {
        val events = listOf(
            ev("com.google.android.play.games", RESUMED, now - 20 * min),
            ev("com.google.android.play.games", PAUSED, now - RecentMode.WINDOW_MS)
        )
        assertEquals(AppMode.GAME, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun launcherAndOverlays_doNotMaskTheApp() {
        val events = listOf(
            ev("com.netflix.ninja", RESUMED, now - 40 * min),
            ev("com.android.systemui", RESUMED, now - 3 * min + 1_000),
            ev("com.netflix.ninja", PAUSED, now - 1 * min),
            ev("dev.tvlauncher", RESUMED, now - 1 * min)
        )
        assertEquals(AppMode.VIDEO, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun lastAppWasNotMedia_isOther_evenIfVideoEarlier() {
        val events = listOf(
            ev("com.netflix.ninja", RESUMED, now - 30 * min),
            ev("com.netflix.ninja", PAUSED, now - 10 * min),
            ev("com.android.vending", RESUMED, now - 10 * min),
            ev("com.android.vending", PAUSED, now - 1 * min)
        )
        assertEquals(AppMode.OTHER, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun stillResumedApp_withinWindow_isUsed() {
        val events = listOf(ev("com.google.android.youtube.tv", RESUMED, now - 2 * min))
        assertEquals(AppMode.VIDEO, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun resumedWithoutPause_olderThanWindow_isOther() {
        // A RESUMED with no PAUSED (lost event) must not show a stale chip.
        val events = listOf(ev("com.google.android.youtube.tv", RESUMED, now - 5 * min))
        assertEquals(AppMode.OTHER, RecentMode.recentMode(events, classify, now))
    }

    @Test
    fun noEvents_isOther() {
        assertEquals(AppMode.OTHER, RecentMode.recentMode(emptyList(), classify, now))
    }

    @Test
    fun chipText_perMode() {
        assertEquals("Watching · Video", RecentMode.chipText(AppMode.VIDEO))
        assertEquals("Gaming", RecentMode.chipText(AppMode.GAME))
        assertEquals("Music", RecentMode.chipText(AppMode.MUSIC))
        assertNull(RecentMode.chipText(AppMode.OTHER))
    }

    @Test
    fun lookback_isSixHours() {
        assertEquals(6L * 3_600_000, RecentMode.LOOKBACK_MS)
        assertEquals(3L * 60_000, RecentMode.WINDOW_MS)
    }
}
