package dev.tvlauncher.weather

object RateLimiter {
    /** True when at least [minIntervalMs] passed since [lastMs] (or the clock went backwards). */
    fun allow(now: Long, lastMs: Long, minIntervalMs: Long): Boolean =
        now < lastMs || now - lastMs >= minIntervalMs
}
