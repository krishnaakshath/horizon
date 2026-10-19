package dev.tvlauncher.safemode

/**
 * What survives a crash: home crash times, the last exception's short class name and the trip
 * record. No stack traces. [trippedAt] / [tripBoot] (Settings.Global.BOOT_COUNT, -1 when unknown)
 * let a trip expire; [retriedAt] is when it last expired; [safeModeCrashTimes] are crashes of safe
 * mode itself; [escapedToSettings] (since [escapedAt] / [escapeBoot]) is the ladder's last level.
 */
data class CrashHistory(
    val times: List<Long> = emptyList(),
    val lastException: String? = null,
    val tripped: Boolean = false,
    val trippedAt: Long = 0L,
    val tripBoot: Int = -1,
    val retriedAt: Long = 0L,
    val safeModeCrashTimes: List<Long> = emptyList(),
    val escapedToSettings: Boolean = false,
    val escapedAt: Long = 0L,
    val escapeBoot: Int = -1
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
 * safe mode; what HomeActivity then does is the [EscapePlan] ladder. A trip ends on "Try normal
 * launcher again" ([reset]) or by itself after a reboot or [EXPIRY_MS]. A real crash loop re-trips
 * within seconds, so an automatic retry is safe. Never throws; a broken store fails open (home).
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

    /** SafeModeActivity itself crashed: the next HOME press climbs one rung of the [EscapePlan]. */
    @Synchronized
    fun recordSafeModeCrash(exceptionClass: String) {
        try {
            val old = read()
            store.write(old.copy(
                safeModeCrashTimes = (old.safeModeCrashTimes + now()).takeLast(MAX_TIMES),
                lastException = exceptionClass.take(MAX_NAME)
            ))
        } catch (e: Throwable) {
        }
    }

    val isTripped: Boolean get() = read().tripped

    val history: CrashHistory get() = read()

    /** HomeActivity's question on every start; persists the ladder's bookkeeping. Fails open to HOME. */
    @Synchronized
    fun decide(): EscapePlan.Level = try {
        val h = read()
        val out = EscapePlan.decide(h, now(), safeBoot())
        if (out.history != h) try { store.write(out.history) } catch (e: Throwable) { }
        out.level
    } catch (e: Throwable) {
        EscapePlan.Level.HOME
    }

    fun shouldRedirect(): Boolean = decide() != EscapePlan.Level.HOME

    /**
     * "Try normal launcher again": clears home crashes and the trip, but keeps recent safe-mode
     * crashes so a stubborn loop still climbs to the system Settings end point.
     */
    @Synchronized
    fun reset() {
        try { store.write(CrashHistory(safeModeCrashTimes = read().safeModeCrashTimes)) } catch (e: Throwable) { }
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

/**
 * The escape ladder, pure. Every HOME press makes progress and ends in a state that is guaranteed
 * usable: the TV's own Settings app, from which the stock launcher can be re-enabled.
 *
 * | Level             | When                                                        |
 * |-------------------|-------------------------------------------------------------|
 * | HOME              | not tripped, or a trip/escape expired (reboot or 24 h)      |
 * | SAFE_MODE_FULL    | tripped, safe mode has not crashed in the last 10 minutes   |
 * | SAFE_MODE_MINIMAL | tripped, safe mode crashed once in the last 10 minutes      |
 * | SYSTEM_SETTINGS   | safe mode crashed twice within 10 minutes; sticky until expiry |
 */
object EscapePlan {
    enum class Level { HOME, SAFE_MODE_FULL, SAFE_MODE_MINIMAL, SYSTEM_SETTINGS }

    data class Outcome(val level: Level, val history: CrashHistory)

    const val SAFE_CRASH_WINDOW_MS = 10 * 60_000L

    /** A reboot (both boot counts known and different), the clock going back, or [CrashGuard.EXPIRY_MS] elapsed. */
    fun expired(sinceMs: Long, sinceBoot: Int, nowMs: Long, bootCount: Int): Boolean {
        if (bootCount >= 0 && sinceBoot >= 0 && bootCount != sinceBoot) return true
        val e = nowMs - sinceMs
        return e < 0 || e >= CrashGuard.EXPIRY_MS
    }

    fun decide(h: CrashHistory, nowMs: Long, bootCount: Int): Outcome {
        val fresh = CrashHistory(lastException = h.lastException, retriedAt = nowMs)
        if (h.escapedToSettings) {
            return if (expired(h.escapedAt, h.escapeBoot, nowMs, bootCount)) Outcome(Level.HOME, fresh)
            else Outcome(Level.SYSTEM_SETTINGS, h)
        }
        if (!h.tripped) return Outcome(Level.HOME, h)
        if (expired(h.trippedAt, h.tripBoot, nowMs, bootCount)) return Outcome(Level.HOME, fresh)
        val safeCrashes = h.safeModeCrashTimes.count { val e = nowMs - it; e in 0 until SAFE_CRASH_WINDOW_MS }
        return when {
            safeCrashes >= 2 -> Outcome(
                Level.SYSTEM_SETTINGS,
                h.copy(escapedToSettings = true, escapedAt = nowMs, escapeBoot = bootCount)
            )
            safeCrashes == 1 -> Outcome(Level.SAFE_MODE_MINIMAL, h)
            else -> Outcome(Level.SAFE_MODE_FULL, h)
        }
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
