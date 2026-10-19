package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test

class RateLimiterTest {
    @Test fun allows_first_then_blocks_within_interval() {
        var t = 1000L
        val r = RateLimiter(45) { t }
        assertTrue(r.tryAcquire())
        t += 44; assertFalse(r.tryAcquire())
        t += 1; assertTrue(r.tryAcquire())
    }

    @Test fun blocked_attempt_does_not_extend_window() {
        var t = 0L
        val r = RateLimiter(45) { t }
        assertTrue(r.tryAcquire())
        t = 30; assertFalse(r.tryAcquire())
        t = 46; assertTrue(r.tryAcquire())
    }
}
