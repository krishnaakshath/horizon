package dev.tvlauncher.safemode

/** What survives a crash: crash times, the last exception's short class name, and the trip flag. No stack traces. */
data class CrashHistory(
    val times: List<Long> = emptyList(),
    val lastException: String? = null,
    val tripped: Boolean = false
) {
    companion object {
        fun encodeTimes(times: List<Long>): String = times.joinToString(",")

        fun decodeTimes(s: String?): List<Long> =
            s.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
    }
}

interface CrashStore {
    fun read(): CrashHistory
    /** Must be synchronous: it runs in a dying process. */
    fun write(history: CrashHistory)
}

/**
 * Pure crash bookkeeping (spec section 6). [THRESHOLD] crashes within [WINDOW_MS] trip safe mode;
 * the trip stays until [reset] ("Try normal launcher again"). Never throws.
 */
class CrashGuard(private val store: CrashStore, private val now: () -> Long) {
    @Synchronized
    fun recordCrash(exceptionClass: String) {
        try {
            val old = read()
            val times = (old.times + now()).takeLast(MAX_TIMES)
            store.write(CrashHistory(times, exceptionClass.take(MAX_NAME), old.tripped || trips(times)))
        } catch (e: Throwable) {
            // Nothing we can do while crashing.
        }
    }

    val isTripped: Boolean get() = read().tripped

    val history: CrashHistory get() = read()

    @Synchronized
    fun reset() {
        try { store.write(CrashHistory()) } catch (e: Throwable) { }
    }

    private fun read(): CrashHistory = try { store.read() } catch (e: Throwable) { CrashHistory() }

    companion object {
        const val THRESHOLD = 3
        const val WINDOW_MS = 60_000L
        const val MAX_TIMES = 5
        const val MAX_NAME = 80

        /** True when any [THRESHOLD] consecutive crashes fall within [WINDOW_MS]. */
        fun trips(times: List<Long>): Boolean {
            val t = times.sorted()
            for (i in 0..t.size - THRESHOLD) if (t[i + THRESHOLD - 1] - t[i] <= WINDOW_MS) return true
            return false
        }
    }
}

/** The redirect decision HomeActivity makes before building anything. */
object SafeModeGate {
    fun shouldRedirect(history: CrashHistory): Boolean = history.tripped
}
