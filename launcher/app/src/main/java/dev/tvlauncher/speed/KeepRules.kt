package dev.tvlauncher.speed

/**
 * Which apps a cleanup may close. Pure. Closing means ActivityManager.killBackgroundProcesses:
 * the system itself refuses to touch anything in front, visible, perceptible (music, a
 * foreground service with its notification) or persistent, so these rules are a second, wider
 * fence on top of that. Only apps with a launcher entry are ever candidates; services, input
 * methods and system components without one are never touched.
 */
object KeepRules {
    enum class Reason(val label: String) {
        OURS("Part of this launcher"),
        SYSTEM("Needed by the TV"),
        LIVE_TV("Live TV"),
        INPUT("Keyboard or input"),
        ACCESSIBILITY("Accessibility"),
        HOME_APP("Home app"),
        SCREENSAVER("Screensaver"),
        PLAYING("Playing"),
        FOREGROUND("On screen"),
        RECENT("Used recently"),
        OWNER("You chose to keep it"),
        NOT_AN_APP("Not an app")
    }

    /** Our own packages: the launcher, the Remote helper and every companion app. */
    const val OWN_PREFIX = "dev.tvlauncher"

    /** Exact system packages that are never closed. */
    val SYSTEM_EXACT = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.settings", "com.android.shell",
        "com.android.bluetooth", "com.android.nfc", "com.android.vending", "com.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller", "com.android.externalstorage", "com.android.keychain",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.ext.services",
        "com.google.android.katniss", "com.google.android.tts", "com.google.android.apps.mediashell",
        "com.google.android.tv.remote.service", "com.google.android.tvrecommendations",
        "com.google.android.backdrop", "com.google.android.leanbacklauncher", "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith",
        "com.google.android.sss.authbridge", "com.google.android.webview", "com.android.webview"
    )

    /** Package prefixes that are never closed: the platform, TV vendor and chipset vendor stacks. */
    val SYSTEM_PREFIXES = listOf(
        "com.android.providers.", "com.android.tv.", "com.android.inputmethod.", "com.google.android.inputmethod.",
        "com.android.dreams.", "com.google.android.tv.", "com.android.server.",
        "com.mediatek.", "com.mstar.", "com.realtek.", "com.amlogic.", "com.droidlogic.", "com.rtk.", "com.hisilicon.",
        "com.nvidia.tegra", "com.qualcomm."
    )

    /** TCL's Live TV, inputs and system apps (each root and everything under it). */
    val LIVE_TV_ROOTS = listOf("com.tcl", "com.tv", "com.android.tv")

    /**
     * Everything a decision needs, gathered by the caller. All sets hold package names.
     * [recent] is only filled for the HOME trigger (apps used within [HomePolicy.RECENT_MS]).
     */
    data class Context(
        val ownPackage: String = OWN_PREFIX,
        val launchable: Set<String> = emptySet(),
        val owner: Set<String> = emptySet(),
        val playing: Set<String> = emptySet(),
        val foreground: Set<String> = emptySet(),
        val inputMethods: Set<String> = emptySet(),
        val accessibility: Set<String> = emptySet(),
        val homeApps: Set<String> = emptySet(),
        val screensaver: Set<String> = emptySet(),
        val recent: Set<String> = emptySet()
    )

    /** Kept for a reason that does not depend on the moment (shown as "Always kept"). */
    fun fixedReason(pkg: String, c: Context): Reason? = when {
        pkg == c.ownPackage || pkg.startsWith(OWN_PREFIX) -> Reason.OURS
        LIVE_TV_ROOTS.any { pkg == it || pkg.startsWith("$it.") } -> Reason.LIVE_TV
        pkg in SYSTEM_EXACT || SYSTEM_PREFIXES.any { pkg.startsWith(it) } -> Reason.SYSTEM
        pkg in c.inputMethods -> Reason.INPUT
        pkg in c.accessibility -> Reason.ACCESSIBILITY
        pkg in c.homeApps -> Reason.HOME_APP
        pkg in c.screensaver -> Reason.SCREENSAVER
        else -> null
    }

    /** Why [pkg] stays open right now, or null when it may be closed. */
    fun reason(pkg: String, c: Context): Reason? {
        if (pkg.isBlank()) return Reason.NOT_AN_APP
        fixedReason(pkg, c)?.let { return it }
        return when {
            pkg in c.playing -> Reason.PLAYING
            pkg in c.foreground -> Reason.FOREGROUND
            pkg in c.owner -> Reason.OWNER
            pkg in c.recent -> Reason.RECENT
            pkg !in c.launchable -> Reason.NOT_AN_APP
            else -> null
        }
    }

    fun closable(pkg: String, c: Context): Boolean = reason(pkg, c) == null
}

/** The list of apps one cleanup asks the system to close. Pure. */
object ClosePlan {
    /** Upper bound per run: a cleanup is a handful of binder calls, never an endless sweep. */
    const val MAX_PER_RUN = 40

    /**
     * With usage access, [lastUsed] (package to last-used time) narrows the list to apps used in
     * the last [windowMs] (others are almost certainly not in memory), most recent first.
     * Without it (null), every launchable app is a candidate: closing an app that is not running
     * is a no-op.
     */
    fun candidates(c: KeepRules.Context, lastUsed: Map<String, Long>?, now: Long, windowMs: Long = 3 * RateLimit.DAY_MS): List<String> {
        val pool = if (lastUsed != null) {
            lastUsed.filter { (_, t) -> t > 0 && now - t in 0..windowMs }.entries
                .sortedByDescending { it.value }.map { it.key }
        } else {
            c.launchable.sorted()
        }
        return pool.filter { KeepRules.closable(it, c) }.distinct().take(MAX_PER_RUN)
    }

    /** The "apps in the background" count for the hint row: used today, not on screen, closable. */
    fun backgroundCount(c: KeepRules.Context, lastUsed: Map<String, Long>?, now: Long): Int? =
        lastUsed?.let { candidates(c, it, now, RateLimit.DAY_MS).size }
}

/** The app on screen, from usage events (oldest first). Pure. */
object ForegroundGuess {
    enum class Kind { FOREGROUND, BACKGROUND }

    data class Event(val pkg: String, val kind: Kind, val at: Long)

    /** The package whose last event is FOREGROUND and is the newest such, or null. */
    fun current(events: List<Event>): String? {
        val last = HashMap<String, Event>()
        for (e in events.sortedBy { it.at }) last[e.pkg] = e
        return last.values.filter { it.kind == Kind.FOREGROUND }.maxByOrNull { it.at }?.pkg
    }
}

/** Parsers for the colon-separated component lists in Settings.Secure. Pure. */
object ComponentLists {
    /** "a.b/.Svc:c.d/c.d.Other" to {"a.b", "c.d"}; blanks and malformed entries are dropped. */
    fun packages(value: String?): Set<String> = value.orEmpty().split(':')
        .mapNotNull { it.trim().substringBefore('/').trim().takeIf { p -> p.isNotEmpty() && p.contains('.') } }
        .toSet()

    /** The owner's list as stored in prefs: comma-separated, trimmed, de-duplicated. */
    fun parseOwner(value: String?): Set<String> =
        value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()

    fun formatOwner(set: Set<String>): String = set.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
}
