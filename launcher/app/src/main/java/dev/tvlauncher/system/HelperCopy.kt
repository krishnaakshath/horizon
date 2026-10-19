package dev.tvlauncher.system

/**
 * Plain words for the Helper rows in Settings. On TCL TVs the memory manager can stop the Helper;
 * only its extras in other apps pause then. Home, power-on and everything in the launcher do not
 * depend on it.
 */
object HelperCopy {
    const val EXTRAS = "Double-Back home, power menu and shortcut key"

    const val LIMIT = "The TV may pause the Helper to save memory. Its extras in other apps then rest until it " +
        "runs again; home, power-on and everything in the launcher keep working."

    /** Subtitle of the Accessibility page's Helper row. */
    fun subtitle(installed: Boolean, on: Boolean): String = when {
        !installed -> "$EXTRAS; see Add-ons"
        on -> "$EXTRAS. If the TV pauses it, home still works"
        else -> "$EXTRAS. Home works without it"
    }

    /** Line under "Open" on the Helper's add-on page. */
    const val OPEN_NOTE = "Double-Back, volume bar and Guide button in other apps"
}
