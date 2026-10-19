package dev.tvlauncher.power

/**
 * Pure reading of recent usage events (oldest first) around the last wake. Used only when the
 * launcher happens to have usage access; otherwise everything stays unknown.
 */
object WakeTimeline {
    enum class Kind { RESUMED, SCREEN_ON, SCREEN_OFF }

    data class Event(val time: Long, val kind: Kind, val pkg: String? = null)

    data class Result(
        /** Last resumed package overall (the foreground), or null. */
        val foreground: String?,
        /** Package in front when the screen went off before the last wake, or null. */
        val beforeSleep: String?,
        /** True when, after the last wake, a package other than the one left from before standby came forward. */
        val navigatedAfterWake: Boolean?
    )

    /**
     * Packages the system or the TV firmware itself brings forward on wake (live TV / last input,
     * its source overlay): their appearing after the wake is not the user's choice.
     */
    val TV_SOURCE_PACKAGES = setOf(
        "com.tcl.tv", "com.tcl.tvinput", "com.android.tv", "com.google.android.tv",
        "com.droidlogic.tvsource", "com.mediatek.wwtv.tvcenter"
    )

    fun analyze(events: List<Event>, ownPackage: String): Result {
        if (events.isEmpty()) return Result(null, null, null)
        val sorted = events.sortedBy { it.time }
        val foreground = sorted.lastOrNull { it.kind == Kind.RESUMED }?.pkg
        val wakeIdx = sorted.indexOfLast { it.kind == Kind.SCREEN_ON }
        if (wakeIdx < 0) return Result(foreground, null, null)
        val offIdx = sorted.subList(0, wakeIdx).indexOfLast { it.kind == Kind.SCREEN_OFF }
        val beforeSleep = sorted.subList(0, if (offIdx >= 0) offIdx else wakeIdx)
            .lastOrNull { it.kind == Kind.RESUMED }?.pkg
        val expected = TV_SOURCE_PACKAGES + ownPackage + listOfNotNull(beforeSleep)
        val navigated = sorted.subList(wakeIdx + 1, sorted.size)
            .any { it.kind == Kind.RESUMED && it.pkg != null && it.pkg !in expected && !isSystemUi(it.pkg) }
        return Result(foreground, beforeSleep, navigated)
    }

    private fun isSystemUi(pkg: String) = pkg == "com.android.systemui" || pkg == "android"
}
