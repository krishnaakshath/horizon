package dev.tvlauncher.ui

import dev.tvlauncher.R
import dev.tvlauncher.spotlight.SpotlightConfig

/**
 * The Spotlight quick-actions row, in focus order. Which actions show and their order come from
 * Settings > Home > Spotlight ([SpotlightConfig]); the default is the original fixed row.
 * Browser and Get Apps appear only when their app is installed. [route] decides whether an item
 * opens one of the home screen's own panels or goes through SystemActions (the launcher's own
 * Settings pages; nothing here opens the TV's settings app).
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
        POWER("Power"),
        SLEEP_TIMER("Sleep timer"),
        RESTART("Restart launcher"),
        GET_APPS("Get Apps"),
        DISCOVER("Discover")
    }

    enum class Route { WIFI_PANEL, BLUETOOTH_PANEL, DEVICES_PANEL, INPUTS_PANEL, AMBIENT, ROOM_CYCLE, DISCOVER, RESTART, SYSTEM }

    /**
     * Wi-Fi, Bluetooth, Devices and Inputs open the home panels directly; Bluetooth without an
     * adapter opens the launcher's Bluetooth page, which says so (and the Inputs panel falls back
     * to the first passthrough input when the panel cannot open). Discover and Restart are
     * handled on the home screen.
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

    /**
     * The launcher Settings section a SYSTEM-routed item opens (null = Settings' own start page).
     * Nothing maps to the TV's settings app.
     */
    fun launcherSection(item: Item): String? = when (item) {
        Item.NETWORK -> "network"
        Item.BLUETOOTH -> "bluetooth"
        Item.SOUND -> dev.tvlauncher.volume.VolumeSection.ID // Settings > Sound > Volume & output
        Item.POWER, Item.SLEEP_TIMER -> "system"
        Item.DEVICES -> "devices"
        Item.ACCESSIBILITY -> "accessibility"
        else -> null
    }

    /** Room lighting cycles in place (OK again picks the next choice), so it never closes Spotlight. */
    fun closesSpotlight(item: Item): Boolean = item != Item.ROOM

    /**
     * What this TV can offer right now. The sleep timer is the launcher's own (TV & system >
     * Power & sleep), so it is there unless a caller says otherwise.
     */
    data class Availability(val installedPackages: Set<String>, val sleepTimer: Boolean = true)

    /** Why [item] cannot show on this TV, or null when it can. */
    fun unavailableReason(item: Item, a: Availability): String? = when (item) {
        Item.BROWSER -> if (BROWSER_PACKAGE in a.installedPackages) null else "Shown once Browser is installed"
        Item.GET_APPS -> if (GET_APPS_PACKAGE in a.installedPackages) null else "Shown once Get Apps is installed"
        Item.SLEEP_TIMER -> if (a.sleepTimer) null else "The sleep timer isn't available"
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
        Item.POWER -> R.drawable.ic_sys_power
        Item.SLEEP_TIMER -> R.drawable.ic_qa_sleep
        Item.RESTART -> R.drawable.ic_set_reset
        Item.GET_APPS -> R.drawable.ic_set_store
        Item.DISCOVER -> R.drawable.ic_disc_bag
    }
}
