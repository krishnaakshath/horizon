package dev.tvlauncher.network

import dev.tvlauncher.R

/**
 * Maps state to the redesigned ic_net_* drawables.
 *
 * HOST WIRING: StatusIcons/TopBar must call [forWifiRssi] (raw dBm) and [forBluetooth] for the pill,
 * the same functions the panel header uses, so pill and panel can never disagree. Do not feed
 * StatusModel's 0..3 level into this object.
 */
object NetIcons {
    enum class BtState { OFF, ON, CONNECTED }

    private val WIFI = intArrayOf(
        R.drawable.ic_net_wifi_0, R.drawable.ic_net_wifi_1, R.drawable.ic_net_wifi_2,
        R.drawable.ic_net_wifi_3, R.drawable.ic_net_wifi_4
    )

    /** [level] is 0..4 (WifiNetworkModel.levelOf); out-of-range values are clamped. */
    fun forWifiLevel(level: Int, connected: Boolean): Int =
        if (!connected) R.drawable.ic_net_wifi_off else WIFI[level.coerceIn(0, WIFI.size - 1)]

    /** Single source of truth: pill and panel header both go through WifiNetworkModel.levelOf. */
    fun forWifiRssi(rssiDbm: Int, connected: Boolean): Int =
        forWifiLevel(WifiNetworkModel.levelOf(rssiDbm), connected)

    fun forBluetooth(state: BtState): Int = when (state) {
        BtState.OFF -> R.drawable.ic_net_bt_off
        BtState.ON -> R.drawable.ic_net_bt_on
        BtState.CONNECTED -> R.drawable.ic_net_bt_connected
    }

    /**
     * The network icon for a connection kind and RSSI: used by BOTH the Wi-Fi panel header and the
     * top-bar network pill. Unknown Wi-Fi signal shows the lowest connected level.
     */
    fun forConnection(kind: WifiConnection.Kind, rssi: Int?): Int =
        forConnectionLevel(kind, rssi?.let { WifiNetworkModel.levelOf(it) })

    /** Same as [forConnection] for an already bucketed 0..4 [level] (null = signal unknown). */
    fun forConnectionLevel(kind: WifiConnection.Kind, level: Int?): Int = when (kind) {
        WifiConnection.Kind.ETHERNET -> ETHERNET
        WifiConnection.Kind.WIFI -> forWifiLevel(level ?: 0, true)
        WifiConnection.Kind.NONE -> forWifiLevel(0, false)
    }

    val ETHERNET = R.drawable.ic_net_ethernet
}
