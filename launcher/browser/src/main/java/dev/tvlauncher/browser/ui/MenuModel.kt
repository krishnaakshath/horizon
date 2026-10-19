package dev.tvlauncher.browser.ui

/**
 * Static definitions of the browser's cards. Destructive cards always list Cancel first and
 * focus it by default, so a stray OK never deletes anything (BACK also cancels).
 */
object Menus {
    enum class Kind { NORMAL, CANCEL, DESTRUCTIVE }
    data class Entry(val id: String, val label: String, val kind: Kind = Kind.NORMAL)
    data class Def(val id: String, val title: String, val entries: List<Entry>)

    private val cancel = Entry("cancel", "Cancel", Kind.CANCEL)

    val CLEAR_DATA = Def("clear_data", "Clear browsing data?", listOf(cancel, Entry("confirm", "Clear now", Kind.DESTRUCTIVE)))
    val CLEAR_EVERYTHING = Def("clear_everything", "Clear everything incl. bookmarks?", listOf(cancel, Entry("confirm", "Clear everything", Kind.DESTRUCTIVE)))
    val DELETE_BOOKMARK = Def("delete_bookmark", "Delete bookmark?", listOf(cancel, Entry("confirm", "Delete", Kind.DESTRUCTIVE)))
    val REMOVE_FAVORITE = Def("remove_favorite", "Remove from favorites?", listOf(cancel, Entry("confirm", "Remove", Kind.DESTRUCTIVE)))
    val CLEAR_HISTORY = Def("clear_history", "Clear history?", listOf(cancel, Entry("confirm", "Clear history", Kind.DESTRUCTIVE)))

    val TILE = Def("tile", "Favorite", listOf(
        Entry("open", "Open"), Entry("new_tab", "Open in new tab"), Entry("left", "Move left"),
        Entry("right", "Move right"), Entry("remove", "Remove from favorites…")
    ))
    val LINK = Def("link", "Link", listOf(
        Entry("new_tab", "Open in new tab"), Entry("open", "Open"), Entry("copy", "Copy link"), Entry("bookmark", "Add bookmark")
    ))

    val ALL = listOf(CLEAR_DATA, CLEAR_EVERYTHING, DELETE_BOOKMARK, REMOVE_FAVORITE, CLEAR_HISTORY, TILE, LINK)
}

object MenuModel {
    /** Destructive cards focus Cancel; everything else focuses the first entry. */
    fun defaultFocusIndex(kinds: List<Menus.Kind>): Int {
        if (Menus.Kind.DESTRUCTIVE in kinds) {
            val c = kinds.indexOf(Menus.Kind.CANCEL)
            if (c >= 0) return c
        }
        return 0
    }
}
