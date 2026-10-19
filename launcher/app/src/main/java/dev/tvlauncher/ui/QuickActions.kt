package dev.tvlauncher.ui

/**
 * The Spotlight quick-actions row, in focus order. Browser appears only when our browser
 * package is installed; Devices and Ambient are always there. [route] decides whether an item
 * opens one of the home screen's own panels or goes through SystemActions (system settings).
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
        DEVICES("Devices"),
        AMBIENT("Ambient"),
        INSIGHTS("Insights"),
        BROWSER("Browser")
    }

    private val ALWAYS = listOf(
        Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.BLUETOOTH, Item.SOUND, Item.DEVICES, Item.AMBIENT, Item.INSIGHTS
    )

    enum class Route { WIFI_PANEL, BLUETOOTH_PANEL, DEVICES_PANEL, AMBIENT, SYSTEM }

    /** Wi-Fi and Bluetooth open the home panels directly; Bluetooth without an adapter uses system settings. */
    fun route(item: Item, bluetoothPresent: Boolean): Route = when (item) {
        Item.NETWORK -> Route.WIFI_PANEL
        Item.BLUETOOTH -> if (bluetoothPresent) Route.BLUETOOTH_PANEL else Route.SYSTEM
        Item.DEVICES -> Route.DEVICES_PANEL
        Item.AMBIENT -> Route.AMBIENT
        else -> Route.SYSTEM
    }

    fun items(installedPackages: Set<String>): List<Item> =
        if (BROWSER_PACKAGE in installedPackages) ALWAYS + Item.BROWSER else ALWAYS
}
