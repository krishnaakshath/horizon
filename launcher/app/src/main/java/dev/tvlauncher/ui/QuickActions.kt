package dev.tvlauncher.ui

/**
 * The Spotlight quick-actions row, in focus order. Browser appears only when our browser
 * package is installed; Devices, Ambient and Room lighting are always there. [route] decides whether an item
 * opens one of the home screen's own panels or goes through SystemActions (the launcher's own
 * Settings pages; nothing here opens the TV's settings app).
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
        /** Cycles room lighting (Auto, Dark room, Evening, Normal, Bright room); the icon shows the choice. */
        ROOM("Room lighting"),
        INSIGHTS("Insights"),
        ACCESSIBILITY("Accessibility"),
        POWER("Power"),
        BROWSER("Browser")
    }

    private val ALWAYS = listOf(
        Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.BLUETOOTH, Item.SOUND, Item.DEVICES, Item.AMBIENT, Item.ROOM, Item.INSIGHTS,
        Item.ACCESSIBILITY, Item.POWER
    )

    enum class Route { WIFI_PANEL, BLUETOOTH_PANEL, DEVICES_PANEL, INPUTS_PANEL, AMBIENT, ROOM_CYCLE, SYSTEM }

    /**
     * Wi-Fi, Bluetooth, Devices and Inputs open the home panels directly; Bluetooth without an
     * adapter opens the launcher's Bluetooth page, which says so (and the Inputs panel falls back
     * to the first passthrough input when the panel cannot open).
     */
    fun route(item: Item, bluetoothPresent: Boolean): Route = when (item) {
        Item.INPUTS -> Route.INPUTS_PANEL
        Item.NETWORK -> Route.WIFI_PANEL
        Item.BLUETOOTH -> if (bluetoothPresent) Route.BLUETOOTH_PANEL else Route.SYSTEM
        Item.DEVICES -> Route.DEVICES_PANEL
        Item.AMBIENT -> Route.AMBIENT
        Item.ROOM -> Route.ROOM_CYCLE
        else -> Route.SYSTEM
    }

    /**
     * The launcher Settings section a SYSTEM-routed item opens (null = Settings' own start page).
     * Nothing maps to the TV's settings app.
     */
    fun launcherSection(item: Item): String? = when (item) {
        Item.NETWORK -> "network"
        Item.BLUETOOTH -> "bluetooth"
        Item.SOUND, Item.POWER -> "system"
        Item.DEVICES -> "devices"
        Item.ACCESSIBILITY -> "accessibility"
        else -> null
    }

    fun items(installedPackages: Set<String>): List<Item> =
        if (BROWSER_PACKAGE in installedPackages) ALWAYS + Item.BROWSER else ALWAYS
}
