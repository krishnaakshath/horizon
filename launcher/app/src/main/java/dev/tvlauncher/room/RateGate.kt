package dev.tvlauncher.room

/**
 * Leading-edge rate limit with a trailing value: [offer] says whether to deliver now; otherwise the
 * value is kept and [trailingDueAt] says when the caller should deliver [takeTrailing]. Pure.
 */
class RateGate(private val minIntervalMs: Long) {
    private var lastAt = Long.MIN_VALUE
    private var held: Float? = null

    /** True: deliver [value] now. False: it is held for the trailing delivery. */
    fun offer(value: Float, now: Long): Boolean {
        if (lastAt == Long.MIN_VALUE || now - lastAt >= minIntervalMs || now < lastAt) {
            lastAt = now
            held = null
            return true
        }
        held = value
        return false
    }

    /** When the held value may be delivered, or null if nothing is held. */
    fun trailingDueAt(): Long? = if (held == null) null else lastAt + minIntervalMs

    /** Returns the held value (and marks it delivered at [now]) or null. */
    fun takeTrailing(now: Long): Float? {
        val v = held ?: return null
        held = null
        lastAt = now
        return v
    }

    fun reset() {
        lastAt = Long.MIN_VALUE
        held = null
    }
}
