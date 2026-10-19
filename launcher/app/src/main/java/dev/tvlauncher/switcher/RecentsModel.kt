package dev.tvlauncher.switcher

import dev.tvlauncher.speed.KeepRules

/** One app the owner used, with the time it was last in front (epoch ms). */
data class RecentUse(val pkg: String, val at: Long)

/**
 * The order of the Recent apps strip. Pure.
 *
 * With usage access the system's last-foreground times are the truth, merged with the launcher's
 * own launch log (whichever is newer wins per app, so an app started a moment ago is first even
 * before usage stats catch up). Without it ([usage] null) only the launch log is used.
 *
 * Never listed: this launcher and its companions, home apps, system parts (settings, keyboards,
 * the platform stack), apps without a launcher entry, and apps the owner closed from the strip
 * that have not been used since.
 */
object RecentsOrder {
    const val DEFAULT_COUNT = 6
    val COUNTS = listOf(5, 6, 8)

    fun build(
        usage: Map<String, Long>?,
        log: List<RecentUse>,
        launchable: Set<String>,
        exclude: (String) -> Boolean,
        closed: Map<String, Long> = emptyMap(),
        max: Int = DEFAULT_COUNT,
        /** "Clear the list": uses at or before this time are left out. */
        since: Long = 0L
    ): List<RecentUse> {
        val latest = HashMap<String, Long>()
        fun offer(pkg: String, at: Long) {
            if (pkg.isBlank() || at <= 0 || at <= since) return
            if (at > (latest[pkg] ?: 0L)) latest[pkg] = at
        }
        usage?.forEach { (p, t) -> offer(p, t) }
        log.forEach { offer(it.pkg, it.at) }
        return latest.entries
            .asSequence()
            .filter { (p, _) -> p in launchable && !exclude(p) }
            .filter { (p, t) -> closed[p]?.let { t > it } ?: true }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(max.coerceAtLeast(0))
            .map { RecentUse(it.key, it.value) }
            .toList()
    }

    /** Usage events (package, time) to each package's last foreground time. */
    fun lastForeground(events: List<RecentUse>): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (e in events) if (e.at > (out[e.pkg] ?: 0L)) out[e.pkg] = e.at
        return out
    }
}

/** Which packages never show in the strip. Pure. */
object RecentsFilter {
    private val HIDDEN = setOf(
        KeepRules.Reason.OURS, KeepRules.Reason.SYSTEM, KeepRules.Reason.HOME_APP,
        KeepRules.Reason.INPUT, KeepRules.Reason.SCREENSAVER, KeepRules.Reason.ACCESSIBILITY
    )

    /** Live TV and the TV maker's own apps stay listed (the owner watches them); they just cannot be closed. */
    fun excluded(pkg: String, c: KeepRules.Context): Boolean =
        pkg.startsWith("com.android.") || KeepRules.fixedReason(pkg, c) in HIDDEN // platform screens such as TV settings
}

/**
 * Whether a card may be closed with ActivityManager.killBackgroundProcesses. Pure. Never this
 * launcher or any system, Live TV, keyboard or home app, never an app that is playing, and
 * nothing at all while something plays that cannot be attributed to an app.
 */
object CloseGuard {
    sealed class Verdict {
        object Allowed : Verdict()
        data class Kept(val why: String) : Verdict()
    }

    const val PLAYING = "is playing, so it stays open"
    const val SOMETHING_PLAYING = "Something is playing, so apps stay open"
    const val NEEDED = "is part of the TV, so it stays open"

    fun check(pkg: String, c: KeepRules.Context, playing: Set<String>, unattributedPlaying: Boolean): Verdict {
        if (KeepRules.fixedReason(pkg, c) != null) return Verdict.Kept(NEEDED)
        if (pkg in playing) return Verdict.Kept(PLAYING)
        if (unattributedPlaying) return Verdict.Kept(SOMETHING_PLAYING)
        return Verdict.Allowed
    }

    /** The calm line shown when a close is refused. */
    fun message(label: String, v: Verdict): String? = when (v) {
        Verdict.Allowed -> null
        is Verdict.Kept -> if (v.why == SOMETHING_PLAYING) v.why else "$label ${v.why}"
    }
}

/**
 * The launcher's own recents (used when usage access is missing): every app it starts, newest
 * first, at most [MAX]. Saved as "pkg=time,pkg=time". Pure codec and update.
 */
object RecentsLog {
    const val MAX = 12

    fun record(log: List<RecentUse>, pkg: String, at: Long): List<RecentUse> {
        if (pkg.isBlank() || pkg.contains(',') || pkg.contains('=')) return log
        return (listOf(RecentUse(pkg, at)) + log.filter { it.pkg != pkg }).take(MAX)
    }

    fun remove(log: List<RecentUse>, pkg: String): List<RecentUse> = log.filter { it.pkg != pkg }

    fun encode(log: List<RecentUse>): String = log.take(MAX).joinToString(",") { "${it.pkg}=${it.at}" }

    /** Tolerant: broken entries are dropped, duplicates keep their newest time, newest first. */
    fun decode(s: String?): List<RecentUse> {
        if (s.isNullOrBlank()) return emptyList()
        val best = LinkedHashMap<String, Long>()
        for (part in s.split(',')) {
            val i = part.lastIndexOf('=')
            if (i <= 0) continue
            val pkg = part.substring(0, i).trim()
            val at = part.substring(i + 1).trim().toLongOrNull() ?: continue
            if (pkg.isEmpty() || at <= 0) continue
            if (at > (best[pkg] ?: 0L)) best[pkg] = at
        }
        return best.entries.map { RecentUse(it.key, it.value) }.sortedByDescending { it.at }.take(MAX)
    }

    /** "pkg=time" pairs of apps closed from the strip (kept until they are used again). */
    fun decodeClosed(s: String?): Map<String, Long> = decode(s).associate { it.pkg to it.at }

    fun encodeClosed(m: Map<String, Long>): String = encode(m.entries.sortedByDescending { it.value }.map { RecentUse(it.key, it.value) })
}

/**
 * "Press Home twice": two Home presses reaching the home screen within [windowMs] open the strip.
 * The first press keeps its usual job (back to a clean home screen). Pure.
 */
class HomePressGesture(private val windowMs: Long = WINDOW_MS) {
    private var last = NEVER

    /** True when this press is the second of a quick pair (the pair is then used up). */
    fun onHome(now: Long, enabled: Boolean): Boolean {
        if (!enabled) { last = NEVER; return false }
        val prev = last
        if (prev != NEVER && now - prev in 0..windowMs) {
            last = NEVER
            return true
        }
        last = now
        return false
    }

    fun reset() { last = NEVER }

    companion object {
        const val WINDOW_MS = 600L
        private const val NEVER = Long.MIN_VALUE
    }
}

/** What OK, long OK and Menu do on a card. Pure. */
object CardKeys {
    enum class Action { NONE, LAUNCH, ASK_CLOSE, CONFIRM_CLOSE, CANCEL_CLOSE, LEAVE }

    /**
     * [confirming]: the focused card is asking "close?". OK launches on release unless it was held
     * (a held OK asks to close instead); Menu asks to close; Back cancels the question or leaves.
     */
    fun onOk(longPress: Boolean, confirming: Boolean): Action = when {
        confirming -> Action.CONFIRM_CLOSE
        longPress -> Action.ASK_CLOSE
        else -> Action.LAUNCH
    }

    fun onMenu(confirming: Boolean): Action = if (confirming) Action.CANCEL_CLOSE else Action.ASK_CLOSE

    fun onBack(confirming: Boolean): Action = if (confirming) Action.CANCEL_CLOSE else Action.LEAVE
}

/** Plain words for the strip. Pure. */
object SwitcherText {
    const val TITLE = "Recent apps"
    const val EMPTY = "Apps you open show up here"
    const val ACCESS_HINT = "Allow usage access in setup"
    const val KEYS = "OK opens · hold OK or Menu to close"
    const val ASK = "OK to close · Back to keep"

    fun closed(label: String) = "Closed $label"
}
