package dev.tvlauncher.ui

import dev.tvlauncher.R
import dev.tvlauncher.spotlight.SpotlightConfig

/**
 * The Spotlight quick-actions row, in focus order. Which actions show and their order come from
 * Settings > Home > Spotlight ([SpotlightConfig]); the default is the original fixed row.
 * Browser and Get Apps appear only when their app is installed, Sleep timer only when the TV
 * has a sleep-timer screen. [route] decides whether an item opens one of the home screen's own
 * panels or goes through SystemActions (system settings).
 */
object QuickActions {
    const val BROWSER_PACKAGE = "dev.tvlauncher.browser"
    const val GET_APPS_PACKAGE = "dev.tvlauncher.store"

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
        BROWSER("Browser"),
        ACCESSIBILITY("Accessibility"),
        SLEEP_TIMER("Sleep timer"),
        RESTART("Restart launcher"),
        GET_APPS("Get Apps"),
        DISCOVER("Discover")
    }

    enum class Route { WIFI_PANEL, BLUETOOTH_PANEL, DEVICES_PANEL, INPUTS_PANEL, AMBIENT, ROOM_CYCLE, DISCOVER, RESTART, SYSTEM }

    /**
     * Wi-Fi, Bluetooth, Devices and Inputs open the home panels directly; Bluetooth without an
     * adapter uses system settings (and the Inputs panel falls back to the system input switch
     * when the TV has no inputs). Discover and Restart are handled on the home screen.
     */
    fun route(item: Item, bluetoothPresent: Boolean): Route = when (item) {
        Item.INPUTS -> Route.INPUTS_PANEL
        Item.NETWORK -> Route.WIFI_PANEL
        Item.BLUETOOTH -> if (bluetoothPresent) Route.BLUETOOTH_PANEL else Route.SYSTEM
        Item.DEVICES -> Route.DEVICES_PANEL
        Item.AMBIENT -> Route.AMBIENT
        Item.ROOM -> Route.ROOM_CYCLE
        Item.DISCOVER -> Route.DISCOVER
        Item.RESTART -> Route.RESTART
        else -> Route.SYSTEM
    }

    /** Room lighting cycles in place (OK again picks the next choice), so it never closes Spotlight. */
    fun closesSpotlight(item: Item): Boolean = item != Item.ROOM

    /** What this TV can offer right now. */
    data class Availability(val installedPackages: Set<String>, val sleepTimer: Boolean = false)

    /** Why [item] cannot show on this TV, or null when it can. */
    fun unavailableReason(item: Item, a: Availability): String? = when (item) {
        Item.BROWSER -> if (BROWSER_PACKAGE in a.installedPackages) null else "Shown once Browser is installed"
        Item.GET_APPS -> if (GET_APPS_PACKAGE in a.installedPackages) null else "Shown once Get Apps is installed"
        Item.SLEEP_TIMER -> if (a.sleepTimer) null else "This TV has no sleep timer screen to open"
        else -> null
    }

    /** The default row (no saved Spotlight settings). */
    fun items(installedPackages: Set<String>): List<Item> = items(Availability(installedPackages), SpotlightConfig.DEFAULT)

    /** The row for [config]: its shown actions, in its order, that this TV can offer. */
    fun items(availability: Availability, config: SpotlightConfig): List<Item> =
        config.shown.filter { unavailableReason(it, availability) == null }

    fun iconRes(item: Item): Int = when (item) {
        Item.INPUTS -> R.drawable.ic_input
        Item.SETTINGS -> R.drawable.ic_settings
        Item.NETWORK -> R.drawable.ic_net_wifi_4
        Item.BLUETOOTH -> R.drawable.ic_net_bt_on
        Item.SOUND -> R.drawable.ic_volume
        Item.DEVICES -> R.drawable.ic_set_devices
        Item.AMBIENT -> R.drawable.ic_qa_ambient
        Item.ROOM -> R.drawable.ic_room_auto
        Item.INSIGHTS -> R.drawable.ic_insights
        Item.BROWSER -> R.drawable.ic_browser
        Item.ACCESSIBILITY -> R.drawable.ic_qa_accessibility
        Item.SLEEP_TIMER -> R.drawable.ic_qa_sleep
        Item.RESTART -> R.drawable.ic_set_reset
        Item.GET_APPS -> R.drawable.ic_set_store
        Item.DISCOVER -> R.drawable.ic_disc_bag
    }
}
