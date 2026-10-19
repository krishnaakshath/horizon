package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherFreshnessTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L

    @Test fun noData_isUnavailable() {
        assertEquals("Weather unavailable", WeatherFreshness.label(null, now))
        assertEquals("Weather unavailable", WeatherFreshness.label(0L, now))
    }

    @Test fun justNow_underOneMinute() {
        assertEquals("Updated just now", WeatherFreshness.label(now, now))
        assertEquals("Updated just now", WeatherFreshness.label(now - 59_999, now))
    }

    @Test fun minutes() {
        assertEquals("Updated 1 min ago", WeatherFreshness.label(now - min, now))
        assertEquals("Updated 5 min ago", WeatherFreshness.label(now - 5 * min - 10_000, now))
        assertEquals("Updated 59 min ago", WeatherFreshness.label(now - 59 * min, now))
    }

    @Test fun hours_untilThreeHours() {
        assertEquals("Updated 1 h ago", WeatherFreshness.label(now - 60 * min, now))
        assertEquals("Updated 2 h ago", WeatherFreshness.label(now - 150 * min, now))
        assertEquals("Updated 3 h ago", WeatherFreshness.label(now - 180 * min, now))
    }

    @Test fun olderThanThreeHours_isUnavailable() {
        assertEquals("Weather unavailable", WeatherFreshness.label(now - 180 * min - 1, now))
        assertFalse(WeatherFreshness.isUsable(now - 180 * min - 1, now))
        assertTrue(WeatherFreshness.isUsable(now - 180 * min, now))
    }

    @Test fun futureTimestamp_clockSkew_isJustNow() {
        assertEquals("Updated just now", WeatherFreshness.label(now + 10 * min, now))
    }
}
