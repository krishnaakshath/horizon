package dev.tvlauncher.insights

/** One foreground transition for an app, normalised from UsageEvents. */
data class UsageEvent(val pkg: String, val type: Type, val timeMs: Long) {
    enum class Type { RESUMED, PAUSED }
}

/** A continuous foreground stretch of one package, already clipped to the query window. */
data class UsageSession(val pkg: String, val startMs: Long, val endMs: Long)

object UsageAggregator {
    const val MIN_SESSION_MS = 1_000L

    /** Foreground milliseconds per package inside [fromMs, toMs]. */
    fun aggregate(events: List<UsageEvent>, fromMs: Long, toMs: Long, ignore: Set<String>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (s in sessions(events, fromMs, toMs, ignore)) out[s.pkg] = (out[s.pkg] ?: 0L) + (s.endMs - s.startMs)
        return out
    }

    fun sessions(events: List<UsageEvent>, fromMs: Long, toMs: Long, ignore: Set<String>): List<UsageSession> {
        if (toMs <= fromMs) return emptyList()
        val sorted = events.filter { it.pkg !in ignore }
            .map { it.copy(timeMs = it.timeMs.coerceIn(fromMs, toMs)) }
            .sortedBy { it.timeMs } // stable: keeps input order for equal timestamps
        val result = ArrayList<UsageSession>()
        val seen = HashSet<String>()
        var openPkg: String? = null
        var openAt = 0L

        fun close(at: Long) {
            val p = openPkg ?: return
            if (at - openAt >= MIN_SESSION_MS) result += UsageSession(p, openAt, at)
            openPkg = null
        }

        for (e in sorted) {
            when (e.type) {
                UsageEvent.Type.RESUMED -> {
                    if (openPkg == e.pkg) { seen += e.pkg; continue } // duplicate: keep original start
                    close(e.timeMs)
                    openPkg = e.pkg; openAt = e.timeMs
                    seen += e.pkg
                }
                UsageEvent.Type.PAUSED -> {
                    if (openPkg == e.pkg) close(e.timeMs)
                    else if (openPkg == null && e.pkg !in seen) {
                        // Already in the foreground when the window opened.
                        openPkg = e.pkg; openAt = fromMs
                        close(e.timeMs)
                    }
                    seen += e.pkg
                }
            }
        }
        close(toMs) // unmatched resume runs to the end of the window
        return result
    }
}
