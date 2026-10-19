package dev.tvlauncher.sound

/** Allows at most one acquisition per [minIntervalMs]; blocked attempts do not move the window. */
class RateLimiter(private val minIntervalMs: Long, private val clock: () -> Long) {
    private var last = Long.MIN_VALUE

    fun tryAcquire(): Boolean {
        val now = clock()
        if (last != Long.MIN_VALUE && now - last < minIntervalMs) return false
        last = now
        return true
    }
}
