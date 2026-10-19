package dev.tvlauncher.weather

/**
 * Pure rules for automatic refreshes triggered by wake, boot, network or the launcher.
 * A trigger starts a network call only when the stored data is older than [STALE_MS], the last
 * call was at least [MIN_GAP_MS] ago, the failure backoff has passed and fewer than
 * [MAX_PER_HOUR] calls happened in the last hour. The 30-minute periodic job is not gated.
 */
object RefreshPolicy {
    const val STALE_MS = 20 * 60_000L
    const val MIN_GAP_MS = 5 * 60_000L
    const val MAX_PER_HOUR = 12
    const val HOUR_MS = 60 * 60_000L
    const val BACKOFF_BASE_MS = 5 * 60_000L
    const val BACKOFF_MAX_MS = 60 * 60_000L

    enum class Decision { REFRESH, NO_PLACE, FRESH, TOO_SOON, BACKING_OFF, HOURLY_CAP }

    /** Missing, zero or future (clock moved back) timestamps count as stale. */
    fun isStale(updatedMs: Long?, now: Long): Boolean {
        if (updatedMs == null || updatedMs <= 0L) return true
        val age = now - updatedMs
        return age < 0L || age > STALE_MS
    }

    /** Wait after [failures] failed calls in a row: 5, 10, 20, 40 min, then 60 min. */
    fun backoffMs(failures: Int): Long {
        if (failures <= 0) return 0L
        val shift = (failures - 1).coerceAtMost(10)
        return (BACKOFF_BASE_MS shl shift).coerceAtMost(BACKOFF_MAX_MS)
    }

    /** [attempts]: start times of recent network calls (any order; future ones are ignored). */
    fun decide(now: Long, hasPlace: Boolean, updatedMs: Long?, attempts: List<Long>, failures: Int): Decision {
        if (!hasPlace) return Decision.NO_PLACE
        if (!isStale(updatedMs, now)) return Decision.FRESH
        val past = attempts.filter { it <= now }
        val last = past.maxOrNull()
        if (last != null && now - last < MIN_GAP_MS) return Decision.TOO_SOON
        if (last != null && now - last < backoffMs(failures)) return Decision.BACKING_OFF
        if (past.count { now - it < HOUR_MS } >= MAX_PER_HOUR) return Decision.HOURLY_CAP
        return Decision.REFRESH
    }
}

/** Which broadcasts may trigger a gated refresh; anything else is ignored. */
object TriggerActions {
    const val TCL_WAKE = "tcl.sys.intent.action.STR_RESUME_COMPLETED"
    const val BOOT = "android.intent.action.BOOT_COMPLETED"
    const val SCREEN_ON = "android.intent.action.SCREEN_ON"
    const val USER_PRESENT = "android.intent.action.USER_PRESENT"

    private val gated = setOf(TCL_WAKE, BOOT, SCREEN_ON, USER_PRESENT, WeatherContract.ACTION_REFRESH_IF_STALE)

    fun triggersRefresh(action: String?): Boolean = action != null && action in gated
}
