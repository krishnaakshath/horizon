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
    val escapeBoot: Int = -1,
    /** SystemClock.elapsedRealtime() at the trip / escape (-1 unknown): a smaller value later means a reboot. */
    val tripElapsed: Long = -1L,
    val escapeElapsed: Long = -1L,
    /** /proc/sys/kernel/random/boot_id at the trip / escape; null when unknown. */
    val tripBootId: String? = null,
    val escapeBootId: String? = null
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
    private val bootCount: () -> Int = { -1 },
    private val elapsedRealtime: () -> Long = { -1L },
    private val bootId: () -> String? = { null }
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
                    tripBoot = if (tripsNow) safeBoot() else old.tripBoot,
                    tripElapsed = if (tripsNow) safeElapsed() else old.tripElapsed,
                    tripBootId = if (tripsNow) safeBootId() else old.tripBootId
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
        val out = EscapePlan.decide(h, now(), safeBoot(), safeElapsed(), safeBootId())
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

    /** Debug reset: forgets everything, including recent safe-mode crashes and the Settings escape. */
    @Synchronized
    fun clearAll() {
        try { store.write(CrashHistory()) } catch (e: Throwable) { }
    }

    private fun safeBoot(): Int = try { bootCount() } catch (e: Throwable) { -1 }

    private fun safeElapsed(): Long = try { elapsedRealtime() } catch (e: Throwable) { -1L }

    private fun safeBootId(): String? = try { BootId.parse(bootId()) } catch (e: Throwable) { null }

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

    /** Where the terminal level sends the owner, in order of preference. */
    enum class Terminal { STOCK_LAUNCHER, SYSTEM_SETTINGS, SAFE_MODE_MINIMAL }

    /**
     * A usable stock launcher (installed, enabled, resolves HOME) comes first, so re-enabling it in
     * Settings takes effect on the next HOME press; then the TV's Settings app; the minimal safe
     * mode only if neither starts.
     */
    fun terminalTargets(stockLauncherAvailable: Boolean): List<Terminal> =
        if (stockLauncherAvailable) listOf(Terminal.STOCK_LAUNCHER, Terminal.SYSTEM_SETTINGS, Terminal.SAFE_MODE_MINIMAL)
        else listOf(Terminal.SYSTEM_SETTINGS, Terminal.SAFE_MODE_MINIMAL)

    /** Boot-start times further apart than this mean a reboot (a clock step this big only expires early, which is safe). */
    const val BOOT_START_DRIFT_MS = 60_000L

    /**
     * Whether the trip/escape recorded at [sinceMs] is over. In order, using only values known on
     * both sides (unknown: -1 / null, never "rebooted"):
     * 1. BOOT_COUNT differs: rebooted.
     * 2. boot_id differs: rebooted; the same boot_id: definitely not rebooted (skip step 3).
     * 3. Arithmetic: uptime went down, or the boot start (wall clock minus uptime) moved by more
     *    than [BOOT_START_DRIFT_MS]: rebooted. A big wall-clock step also lands here; expiring
     *    then is safe because a real crash loop re-trips within seconds.
     * 4. The clock went back, or [CrashGuard.EXPIRY_MS] passed.
     */
    fun expired(
        sinceMs: Long, sinceBoot: Int, sinceElapsed: Long, nowMs: Long, bootCount: Int, elapsedNow: Long,
        sinceBootId: String? = null, bootIdNow: String? = null
    ): Boolean {
        if (bootCount >= 0 && sinceBoot >= 0 && bootCount != sinceBoot) return true
        val sameBootId = sinceBootId != null && bootIdNow != null
        if (sameBootId && sinceBootId != bootIdNow) return true
        if (!sameBootId && sinceElapsed >= 0 && elapsedNow >= 0) {
            if (elapsedNow < sinceElapsed) return true
            val drift = (nowMs - elapsedNow) - (sinceMs - sinceElapsed)
            if (drift > BOOT_START_DRIFT_MS || drift < -BOOT_START_DRIFT_MS) return true
        }
        val e = nowMs - sinceMs
        return e < 0 || e >= CrashGuard.EXPIRY_MS
    }

    fun decide(h: CrashHistory, nowMs: Long, bootCount: Int, elapsedNow: Long = -1L, bootIdNow: String? = null): Outcome {
        val fresh = CrashHistory(lastException = h.lastException, retriedAt = nowMs)
        if (h.escapedToSettings) {
            return if (expired(h.escapedAt, h.escapeBoot, h.escapeElapsed, nowMs, bootCount, elapsedNow, h.escapeBootId, bootIdNow)) Outcome(Level.HOME, fresh)
            else Outcome(Level.SYSTEM_SETTINGS, h)
        }
        if (!h.tripped) return Outcome(Level.HOME, h)
        if (expired(h.trippedAt, h.tripBoot, h.tripElapsed, nowMs, bootCount, elapsedNow, h.tripBootId, bootIdNow)) return Outcome(Level.HOME, fresh)
        val safeCrashes = h.safeModeCrashTimes.count { val e = nowMs - it; e in 0 until SAFE_CRASH_WINDOW_MS }
        return when {
            safeCrashes >= 2 -> Outcome(
                Level.SYSTEM_SETTINGS,
                h.copy(
                    escapedToSettings = true, escapedAt = nowMs, escapeBoot = bootCount,
                    escapeElapsed = elapsedNow, escapeBootId = bootIdNow
                )
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
    /**
     * Safe mode stopped while still in charge: the user opened another app from it. A later crash
     * in the background (a service, a receiver) is then nobody's: not home, not safe mode.
     */
    fun onStopped(name: String) { if (name == safeModeClass && current == safeModeClass) current = NOBODY }
    @Suppress("UNUSED_PARAMETER") fun onDestroyed(name: String) = Unit

    /** Process start, or the home screen in charge. Any other (also unknown) screen: not recorded. */
    fun shouldRecordCrash(): Boolean = current.let { it == null || it == homeClass }

    fun inSafeMode(): Boolean = current == safeModeClass

    private companion object {
        /** Not null (process start) and never an activity name: crashes are ignored. */
        const val NOBODY = ""
    }
}

/**
 * What the uncaught handler does with a crash. A crash in safe mode is a safe-mode crash. While the
 * guard is tripped, the home screen never runs (HomeActivity redirects first), so a crash charged
 * to home or to process start is safe mode failing before it could report itself (class init,
 * verify error, attach or theme; on API 24-28 anything before its super.onCreate returns): it is
 * counted as a safe-mode crash too, so the ladder keeps climbing. Other screens are ignored.
 */
object CrashAttribution {
    enum class Kind { HOME, SAFE_MODE, IGNORE }

    fun classify(scope: CrashScope, tripped: Boolean): Kind = when {
        scope.inSafeMode() -> Kind.SAFE_MODE
        scope.shouldRecordCrash() -> if (tripped) Kind.SAFE_MODE else Kind.HOME
        else -> Kind.IGNORE
    }
}

/** The kernel's per-boot random id, the most reliable reboot signal when readable. */
object BootId {
    private const val PATH = "/proc/sys/kernel/random/boot_id"

    /** Trimmed id when it has the expected shape (8..64 hex digits and dashes), else null. */
    fun parse(text: String?): String? {
        val t = text?.trim() ?: return null
        if (t.length !in 8..64) return null
        return if (t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == '-' }) t else null
    }

    /** Never throws; null when unreadable. */
    fun read(): String? = try { parse(java.io.File(PATH).readText()) } catch (e: Throwable) { null }
}
