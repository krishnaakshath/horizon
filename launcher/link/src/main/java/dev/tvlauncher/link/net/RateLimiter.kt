package dev.tvlauncher.link.net

/**
 * Token buckets per key (a client address): [capacity] requests at once, refilled at
 * [perSecond]. Pure (time is passed in) and bounded: at most [maxKeys] buckets, the stalest
 * dropped first.
 */
class RateLimiter(
    private val capacity: Double,
    private val perSecond: Double,
    private val maxKeys: Int = 64,
) {
    private class Bucket(var tokens: Double, var at: Long)

    private val buckets = LinkedHashMap<String, Bucket>(16, 0.75f, true)

    @Synchronized
    fun tryAcquire(key: String, nowMs: Long, cost: Double = 1.0): Boolean {
        val b = buckets.getOrPut(key) {
            if (buckets.size >= maxKeys) buckets.keys.firstOrNull()?.let { buckets.remove(it) }
            Bucket(capacity, nowMs)
        }
        val elapsed = (nowMs - b.at).coerceAtLeast(0L) / 1000.0
        b.tokens = (b.tokens + elapsed * perSecond).coerceAtMost(capacity)
        b.at = nowMs
        if (b.tokens < cost) return false
        b.tokens -= cost
        return true
    }
}
