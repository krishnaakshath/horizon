package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

class RateLimiterTest {
    @Test fun allows() {
        assertTrue(RateLimiter.allow(now = 1_000_000, lastMs = 0, minIntervalMs = 120_000))
        assertFalse(RateLimiter.allow(now = 100_000, lastMs = 50_000, minIntervalMs = 120_000))
        assertTrue(RateLimiter.allow(now = 170_000, lastMs = 50_000, minIntervalMs = 120_000))
    }

    @Test fun clockWentBackwardsAllows() {
        assertTrue(RateLimiter.allow(now = 10, lastMs = 5_000_000, minIntervalMs = 120_000))
    }
}
