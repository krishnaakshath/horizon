package dev.tvlauncher.appsettings

/** One line of the sidebar: a group header (OK expands/collapses) or a page. */
sealed class SidebarEntry {
    data class Header(val group: SettingsGroup, val expanded: Boolean) : SidebarEntry()
    data class Page(val id: String, val group: SettingsGroup) : SidebarEntry()
}

/**
 * The grouped sidebar as an accordion: at most one group is open, by default the one holding
 * the [current] page. Collapsed pages are simply not in [entries], so DPAD_DOWN skips them.
 */
data class SidebarState(
    val groups: List<Pair<SettingsGroup, List<String>>>,
    val current: String?,
    val expanded: SettingsGroup?
) {
    val entries: List<SidebarEntry> = groups.flatMap { (g, ids) ->
        val open = g == expanded
        listOf<SidebarEntry>(SidebarEntry.Header(g, open)) + if (open) ids.map { SidebarEntry.Page(it, g) } else emptyList()
    }

    fun groupOf(id: String?): SettingsGroup? = groups.firstOrNull { id != null && id in it.second }?.first

    /** OK on a header: open it (closing the others) or close it. */
    fun toggle(group: SettingsGroup): SidebarState = copy(expanded = if (expanded == group) null else group)

    fun select(id: String): SidebarState = copy(current = id, expanded = groupOf(id) ?: expanded)

    /** Re-opens the current page's group (focus returning from the pane needs its entry). */
    fun reveal(): SidebarState = copy(expanded = groupOf(current) ?: expanded)

    fun indexOfPage(id: String): Int = entries.indexOfFirst { it is SidebarEntry.Page && it.id == id }

    fun indexOfHeader(group: SettingsGroup): Int = entries.indexOfFirst { it is SidebarEntry.Header && it.group == group }

    /** Where [entry] (from an earlier state) is now: a header by group, a page by id, else its group's header. */
    fun indexOf(entry: SidebarEntry): Int = when (entry) {
        is SidebarEntry.Header -> indexOfHeader(entry.group)
        is SidebarEntry.Page -> indexOfPage(entry.id).takeIf { it >= 0 } ?: indexOfHeader(entry.group)
    }

    fun move(from: Int, delta: Int): Int = if (entries.isEmpty()) -1 else (from + delta).coerceIn(0, entries.lastIndex)

    companion object {
        fun initial(groups: List<Pair<SettingsGroup, List<String>>>, current: String?): SidebarState {
            val s = SidebarState(groups, current, null)
            return s.copy(expanded = s.groupOf(current))
        }

        /**
         * New groups (an app was installed or removed) without undoing the user's choice: the open
         * group stays open while it still exists, a collapsed one stays collapsed; only when the
         * open group is gone does the current page's group open.
         */
        fun regroup(old: SidebarState, groups: List<Pair<SettingsGroup, List<String>>>, current: String?): SidebarState {
            val keep = old.expanded?.takeIf { g -> groups.any { it.first == g } }
            val s = SidebarState(groups, current, keep)
            return if (old.expanded != null && keep == null) s.copy(expanded = s.groupOf(current)) else s
        }

        /** Restored state, then the page asked for by the caller, then the last page, then the first. */
        fun startPage(saved: String?, requested: String?, last: String?, available: List<String>): String? =
            listOf(saved, requested, last).firstOrNull { it != null && it in available } ?: available.firstOrNull()
    }
}

/** The small path above a page title; it never repeats the title itself. */
object Breadcrumb {
    const val SEP = " › "

    /**
     * Only sub-pages get a trail ("Home layout › Favorites"); a section's root page shows none, since
     * the sidebar already names its group. [group] is kept for callers; it is not repeated.
     */
    @Suppress("UNUSED_PARAMETER")
    fun of(group: String, section: String, stack: List<String>): String =
        if (stack.isEmpty()) "" else (listOf(section) + stack).joinToString(SEP)
}

/** Open/closed state of a page's single "Advanced" block (closed by default). */
data class AdvancedToggle(val expanded: Boolean = false) {
    fun toggle() = AdvancedToggle(!expanded)

    /** Opening moves focus to the first advanced row; closing leaves it on the toggle. */
    fun focusAfterToggle(toggleIndex: Int, advancedCount: Int): Int =
        if (expanded && advancedCount > 0) toggleIndex + 1 else toggleIndex

    /** Focusable rows on screen: the primary rows, the toggle (if anything is advanced) and open advanced rows. */
    fun visibleRows(primary: Int, advanced: Int): Int =
        primary + if (advanced == 0) 0 else 1 + if (expanded) advanced else 0
}
