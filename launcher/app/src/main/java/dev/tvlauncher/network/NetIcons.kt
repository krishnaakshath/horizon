package dev.tvlauncher.network

import dev.tvlauncher.R

/** Maps state to the redesigned ic_net_* drawables. The top bar adopts these (see report). */
object NetIcons {
    enum class BtState { OFF, ON, CONNECTED }

    private val WIFI = intArrayOf(
        R.drawable.ic_net_wifi_0, R.drawable.ic_net_wifi_1, R.drawable.ic_net_wifi_2,
        R.drawable.ic_net_wifi_3, R.drawable.ic_net_wifi_4
    )

    /** [level] is 0..4 (WifiNetworkModel.levelOf); out-of-range values are clamped. */
    fun forWifi(level: Int, connected: Boolean): Int =
        if (!connected) R.drawable.ic_net_wifi_off else WIFI[level.coerceIn(0, WIFI.size - 1)]

    fun forBluetooth(state: BtState): Int = when (state) {
        BtState.OFF -> R.drawable.ic_net_bt_off
        BtState.ON -> R.drawable.ic_net_bt_on
        BtState.CONNECTED -> R.drawable.ic_net_bt_connected
    }

    val ETHERNET = R.drawable.ic_net_ethernet
}
