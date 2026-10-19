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

    fun move(from: Int, delta: Int): Int = if (entries.isEmpty()) -1 else (from + delta).coerceIn(0, entries.lastIndex)

    companion object {
        fun initial(groups: List<Pair<SettingsGroup, List<String>>>, current: String?): SidebarState {
            val s = SidebarState(groups, current, null)
            return s.copy(expanded = s.groupOf(current))
        }

        /** Restored state, then the page asked for by the caller, then the last page, then the first. */
        fun startPage(saved: String?, requested: String?, last: String?, available: List<String>): String? =
            listOf(saved, requested, last).firstOrNull { it != null && it in available } ?: available.firstOrNull()
    }
}

/** The small path above a page title; it never repeats the title itself. */
object Breadcrumb {
    const val SEP = " › "

    fun of(group: String, section: String, stack: List<String>): String =
        (listOf(group) + if (stack.isEmpty()) emptyList() else listOf(section) + stack.dropLast(1)).joinToString(SEP)
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
