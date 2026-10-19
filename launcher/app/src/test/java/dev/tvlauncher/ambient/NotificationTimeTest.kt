package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTimeTest {
    private val now = 1_800_000_000_000L
    private val m = 60_000L

    @Test fun shortLabels() {
        assertEquals("now", NotificationTime.short(now, now))
        assertEquals("now", NotificationTime.short(now - 59_999, now))
        assertEquals("1m", NotificationTime.short(now - m, now))
        assertEquals("5m", NotificationTime.short(now - 5 * m - 30_000, now))
        assertEquals("59m", NotificationTime.short(now - 59 * m, now))
        assertEquals("1h", NotificationTime.short(now - 60 * m, now))
        assertEquals("23h", NotificationTime.short(now - (24 * 60 - 1) * m, now))
        assertEquals("1d", NotificationTime.short(now - 24 * 60 * m, now))
    }

    @Test fun futureOrMissing() {
        assertEquals("now", NotificationTime.short(now + 5 * m, now))
        assertEquals("", NotificationTime.short(0, now))
    }
}
