package dev.tvlauncher.ui

/**
 * The Spotlight quick-actions row, in focus order. Browser appears only when our browser
 * package is installed. Replaces the old floating dock's item model.
 */
object QuickActions {
    const val BROWSER_PACKAGE = "dev.tvlauncher.browser"

    enum class Item(val title: String, val hasLongPress: Boolean = false) {
        INPUTS("Inputs"),
        /** Long-press: "Unhide all apps". */
        SETTINGS("Settings", hasLongPress = true),
        NETWORK("Network"),
        BLUETOOTH("Bluetooth"),
        SOUND("Sound"),
        INSIGHTS("Insights"),
        BROWSER("Browser")
    }

    private val ALWAYS = listOf(Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.BLUETOOTH, Item.SOUND, Item.INSIGHTS)

    fun items(installedPackages: Set<String>): List<Item> =
        if (BROWSER_PACKAGE in installedPackages) ALWAYS + Item.BROWSER else ALWAYS
}
