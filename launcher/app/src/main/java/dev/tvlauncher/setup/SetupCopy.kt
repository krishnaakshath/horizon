package dev.tvlauncher.setup

/**
 * Every user-facing line of the setup flow, plus the calm replacements other screens use where
 * they once showed command lines. Plain words only: no commands, no package names, no jargon
 * (SetupCopyTest enforces it).
 */
object SetupCopy {
    const val SCREEN_TITLE = "Set up your TV"
    const val SCREEN_LEAD = "A few quick steps. Everything is optional and you can come back any time in Settings."
    const val FINISH = "Go to Home"
    const val SKIP = "Skip"
    const val ALL_SET = "All set. Enjoy your TV."

    /** One calm line wherever something can only be switched on by the one-time setup helper. */
    const val HELPER_LINE = "Finish setup to enable. This TV has no screen for this; the one-time setup helper switches it on."

    const val HOME_TITLE = "Home button"
    const val HOME_DONE = "Pressing Home opens TV Launcher."
    const val HOME_ACTION = "Make TV Launcher the home screen."
    const val HOME_LABEL = "Choose home screen"
    const val HOME_HELPER = "This TV keeps its own home screen. The one-time setup helper can switch it."

    const val NP_TITLE = "Now Playing"
    const val NP_DONE = "Shows what is playing and recent notifications."
    const val NP_AUTO = "Show what is playing on the home screen."
    const val NP_ACTION = "Turn on TV Launcher in the list that opens, then press Back."
    const val NP_LABEL = "Allow Now Playing"
    const val NP_HELPER = HELPER_LINE

    const val SC_TITLE = "Remote shortcuts"
    const val SC_DONE = "Double-Back to Home is ready."
    const val SC_AUTO = "Turn on the Helper app's remote shortcuts."
    const val SC_ACTION = "Turn on Launcher Helper in the list that opens, then press Back."
    const val SC_LABEL = "Turn on double-Back and power-on help"
    const val SC_HELPER = HELPER_LINE

    const val NEAR_TITLE = "Wi-Fi and Bluetooth lists"
    const val NEAR_DONE = "Nearby networks and devices can be listed."
    const val NEAR_ACTION = "Lets the launcher list nearby networks and devices. Your position is never read."
    const val NEAR_LABEL = "Allow"
    const val NEAR_HELPER = "Allow Location for TV Launcher in the TV's app settings to list nearby networks."

    const val PH_TITLE = "Your photos"
    const val PH_DONE = "Pictures on this TV or a USB stick can be wallpapers."
    const val PH_ACTION = "Use pictures from this TV or a USB stick as wallpapers."
    const val PH_LABEL = "Allow photos"
    const val PH_HELPER = "Allow Files and media for TV Launcher in the TV's app settings to use your photos."

    const val WX_TITLE = "Weather"
    const val WX_DONE = "The Weather app keeps your city."
    const val WX_ACTION = "Choose your city in the Weather app."
    const val WX_LABEL = "Set city"

    const val APPS_TITLE = "Companion apps"
    const val APPS_DONE = "Browser, Get Apps, Weather and Helper are installed."
    const val APPS_LABEL = "Get apps"

    const val IN_TITLE = "Watch-time insights"
    const val IN_DONE = "Insights can show what you watch most."
    const val IN_ACTION = "Allow TV Launcher in the list that opens, then press Back."
    const val IN_LABEL = "Allow insights"
    const val IN_HELPER = HELPER_LINE

    const val TUNED_TITLE = "Tuned for this TV"

    /** Settings > System > Setup. */
    const val SECTION_TITLE = "Setup"
    const val SECTION_DESCRIPTION = "Run the first-time checklist again."
    const val SECTION_RUN = "Run setup again"

    // Replacements for lines other screens used to show.
    const val LOCATION_NEEDED = "Allow TV Launcher to list nearby networks and devices. Your position is never read."
    const val LOCATION_ALLOW = "Allow"
    const val LOCATION_BLOCKED = NEAR_HELPER
    const val SCREENSAVER_NOTE = "To use Ambient as the screensaver, pick TV Launcher Ambient in the TV's screen saver settings."
    const val PHOTOS_NOTE = "Copy a folder of photos to Pictures/TVLauncher, or to a TVLauncher folder on a USB stick."
    const val INSTALL_NOTE = "Open the app's file from a USB drive with a file manager and allow the install when asked."

    fun appsMissing(titles: List<String>) = "Not installed yet: ${titles.joinToString(", ")}."

    fun appsHelper(titles: List<String>) = "Not installed yet: ${titles.joinToString(", ")}. The setup helper installs them."

    fun tuned(lite: Boolean, roomAutomatic: Boolean): String =
        (if (lite) "Lite effects for smooth motion" else "Full effects") +
            (if (roomAutomatic) ", automatic room lighting" else ", your room lighting choice") +
            " and gentle sounds."

    /** Notification-access hint after trying to open the TV's access screen. */
    fun notificationAccessHint(settingsOpened: Boolean): String =
        if (settingsOpened) NP_ACTION else HELPER_LINE
}
