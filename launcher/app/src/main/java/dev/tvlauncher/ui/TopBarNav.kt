package dev.tvlauncher.ui

/**
 * DPAD_DOWN from the top-bar pills (Inputs, Settings). With an empty shelf there is nothing
 * below, so Down opens Spotlight: a D-pad-only remote always reaches Settings.
 */
object TopBarNav {
    enum class Action { FOCUS_SHELF, OPEN_SPOTLIGHT }

    fun onDown(shelfEmpty: Boolean): Action = if (shelfEmpty) Action.OPEN_SPOTLIGHT else Action.FOCUS_SHELF
}
