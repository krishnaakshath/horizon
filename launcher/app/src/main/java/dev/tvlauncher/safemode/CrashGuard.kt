package dev.tvlauncher.safemode

/**
 * What survives a crash: crash times, the last exception's short class name and the trip record.
 * No stack traces. [trippedAt] / [tripBoot] (Settings.Global.BOOT_COUNT, -1 when unknown) let a
 * trip expire; [retriedAt] is when it last expired; [safeModeCrashed] means safe mode itself crashed.
 */
data class CrashHistory(
    val times: List<Long> = emptyList(),
    val lastException: String? = null,
    val tripped: Boolean = false,
    val trippedAt: Long = 0L,
    val tripBoot: Int = -1,
    val retriedAt: Long = 0L,
    val safeModeCrashed: Boolean = false
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
 * Pure crash bookkeeping (spec section 6). [THRESHOLD] home-screen crashes within [WINDOW_MS] trip
 * safe mode. A trip ends on "Try normal launcher again" ([reset]) or by itself once (see
 * [SafeModeGate]): after a reboot, after [EXPIRY_MS], or when safe mode itself crashed. A real
 * crash loop re-trips within seconds, so an automatic retry is safe. Never throws.
 */
class CrashGuard(
    private val store: CrashStore,
    private val now: () -> Long,
    private val bootCount: () -> Int = { -1 }
) {
    /** A crash that [CrashScope] attributes to the home screen (or to process start). */
    @Synchronized
    fun recordCrash(exceptionClass: String) {
        try {
            val old = read()
            val t = now()
            val times = (old.times + t).takeLast(MAX_TIMES)
            val tripsNow = !old.tripped && trips(times)
            store.write(
                old.copy(
                    times = times,
                    lastException = exceptionClass.take(MAX_NAME),
                    tripped = old.tripped || tripsNow,
                    trippedAt = if (tripsNow) t else old.trippedAt,
                    tripBoot = if (tripsNow) safeBoot() else old.tripBoot
                )
            )
        } catch (e: Throwable) {
            // Nothing we can do while crashing.
        }
    }

    /** SafeModeActivity itself crashed: the next HOME press must not land in it again. */
    @Synchronized
    fun recordSafeModeCrash(exceptionClass: String) {
        try {
            store.write(read().copy(safeModeCrashed = true, lastException = exceptionClass.take(MAX_NAME)))
        } catch (e: Throwable) {
        }
    }

    val isTripped: Boolean get() = read().tripped

    val history: CrashHistory get() = read()

    /**
     * HomeActivity's question on every start. On [SafeModeGate.Decision.RETRY_HOME] the trip, the
     * crash times and the safe-mode flag are cleared once, so three fresh crashes are needed to trip again.
     */
    @Synchronized
    fun shouldRedirect(): Boolean {
        val h = read()
        return when (SafeModeGate.decide(h, now(), safeBoot())) {
            SafeModeGate.Decision.SAFE_MODE -> true
            SafeModeGate.Decision.HOME -> false
            SafeModeGate.Decision.RETRY_HOME -> {
                try { store.write(CrashHistory(lastException = h.lastException, retriedAt = now())) } catch (e: Throwable) { }
                false
            }
        }
    }

    @Synchronized
    fun reset() {
        try { store.write(CrashHistory()) } catch (e: Throwable) { }
    }

    private fun read(): CrashHistory = try { store.read() } catch (e: Throwable) { CrashHistory() }

    private fun safeBoot(): Int = try { bootCount() } catch (e: Throwable) { -1 }

    companion object {
        const val THRESHOLD = 3
        /** Wide enough for Android's own home-crash handling (dialog, re-pick) between crashes. */
        const val WINDOW_MS = 3 * 60_000L
        const val EXPIRY_MS = 24 * 60 * 60_000L
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

/** The pure redirect decision HomeActivity makes before building anything. */
object SafeModeGate {
    enum class Decision { HOME, SAFE_MODE, RETRY_HOME }

    fun decide(h: CrashHistory, nowMs: Long, bootCount: Int): Decision = when {
        h.safeModeCrashed -> Decision.RETRY_HOME // a broken safe mode must never trap the owner
        !h.tripped -> Decision.HOME
        bootCount >= 0 && h.tripBoot >= 0 && bootCount != h.tripBoot -> Decision.RETRY_HOME
        nowMs - h.trippedAt >= CrashGuard.EXPIRY_MS -> Decision.RETRY_HOME
        else -> Decision.SAFE_MODE
    }
}

/**
 * Which activity a crash belongs to, fed by ActivityLifecycleCallbacks with class names. Only
 * crashes of the home screen (or before any activity exists, i.e. process start) count toward
 * safe mode; a crash in Settings, Insights or any other screen of ours does not lock the owner out.
 * The latest activity to be created, started or resumed is the one in charge; pause, stop and
 * destroy do not change that, so "home behind another app" still counts as home.
 */
class CrashScope(private val homeClass: String, private val safeModeClass: String) {
    @Volatile private var current: String? = null

    fun onCreated(name: String) { current = name }
    fun onStarted(name: String) { current = name }
    fun onResumed(name: String) { current = name }
    @Suppress("UNUSED_PARAMETER") fun onPaused(name: String) = Unit
    @Suppress("UNUSED_PARAMETER") fun onStopped(name: String) = Unit
    @Suppress("UNUSED_PARAMETER") fun onDestroyed(name: String) = Unit

    /** Process start, or the home screen in charge. Any other (also unknown) screen: not recorded. */
    fun shouldRecordCrash(): Boolean = current.let { it == null || it == homeClass }

    fun inSafeMode(): Boolean = current == safeModeClass
}
