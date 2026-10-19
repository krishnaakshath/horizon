package dev.tvlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {
    private val min = 60_000L
    @Test fun formats() {
        assertEquals("just now", TimeAgo.format(1000 * min + 20_000, 1000 * min))
        assertEquals("1 min ago", TimeAgo.format(1001 * min, 1000 * min))
        assertEquals("45 min ago", TimeAgo.format(1045 * min, 1000 * min))
        assertEquals("1 h ago", TimeAgo.format(1060 * min, 1000 * min))
        assertEquals("5 h ago", TimeAgo.format(1000 * min + 5 * 60 * min, 1000 * min))
        assertEquals("2 d ago", TimeAgo.format(1000 * min + 48 * 60 * min, 1000 * min))
    }
    @Test fun futureIsJustNow() = assertEquals("just now", TimeAgo.format(0, 5 * min))
}
