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
    fun forWifiLevel(level: Int, connected: Boolean): Int =
        if (!connected) R.drawable.ic_net_wifi_off else WIFI[level.coerceIn(0, WIFI.size - 1)]

    /** Single source of truth: pill and panel header both go through WifiNetworkModel.levelOf. */
    fun forWifiRssi(rssiDbm: Int, connected: Boolean): Int =
        forWifiLevel(WifiNetworkModel.levelOf(rssiDbm), connected)

    /** Kept for existing callers; same as [forWifiLevel]. */
    fun forWifi(level: Int, connected: Boolean): Int = forWifiLevel(level, connected)

    /**
     * Adapter for a host that still holds StatusModel's 0..3 bucket. Order-preserving and spread
     * over the 0..4 scale: 0->0, 1->2, 2->3, 3->4 (3 is "at or above -55 dBm" in StatusModel, i.e.
     * full strength). Prefer [forWifiRssi] so both views use the same 5 buckets.
     */
    fun levelFromStatusModel(statusLevel0to3: Int): Int = when (statusLevel0to3.coerceIn(0, 3)) {
        0 -> 0
        1 -> 2
        2 -> 3
        else -> 4
    }

    fun forBluetooth(state: BtState): Int = when (state) {
        BtState.OFF -> R.drawable.ic_net_bt_off
        BtState.ON -> R.drawable.ic_net_bt_on
        BtState.CONNECTED -> R.drawable.ic_net_bt_connected
    }

    val ETHERNET = R.drawable.ic_net_ethernet
}
