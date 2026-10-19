package dev.tvlauncher.ui

import dev.tvlauncher.network.NetIcons
import dev.tvlauncher.network.WifiConnection

/**
 * Pure status-bar rules. The top-bar pills use [Status] and the [networkPillIcon] /
 * [bluetoothPillIcon] mappings, which go through [NetIcons] exactly like the Wi-Fi and Bluetooth
 * panel headers do. The older 0..3 [wifiLevel] / [NetIcon] model is kept only for the Settings
 * network summary text; it never feeds the pills.
 */
object StatusModel {
    /** Live state read by StatusIcons. [bt] is null when the TV has no Bluetooth adapter. */
    data class Status(val kind: WifiConnection.Kind, val rssi: Int?, val wifiEnabled: Boolean, val bt: NetIcons.BtState?)

    /** Same rule as WifiController: Ethernet wins over Wi-Fi; anything else is NONE. */
    fun connectionKind(hasNetwork: Boolean, ethernet: Boolean, wifi: Boolean): WifiConnection.Kind = when {
        !hasNetwork -> WifiConnection.Kind.NONE
        ethernet -> WifiConnection.Kind.ETHERNET
        wifi -> WifiConnection.Kind.WIFI
        else -> WifiConnection.Kind.NONE
    }

    fun btState(present: Boolean, enabled: Boolean, connected: Boolean): NetIcons.BtState? = when {
        !present -> null
        !enabled -> NetIcons.BtState.OFF
        connected -> NetIcons.BtState.CONNECTED
        else -> NetIcons.BtState.ON
    }

    fun networkPillIcon(s: Status): Int = NetIcons.forConnection(s.kind, s.rssi)

    /** Null hides the Bluetooth pill (no adapter). */
    fun bluetoothPillIcon(s: Status): Int? = s.bt?.let { NetIcons.forBluetooth(it) }

    fun networkLabel(s: Status): String = when (s.kind) {
        WifiConnection.Kind.ETHERNET -> "Ethernet"
        WifiConnection.Kind.WIFI -> "Wi-Fi"
        WifiConnection.Kind.NONE -> if (s.wifiEnabled) "Not connected" else "Offline"
    }

    enum class NetKind { WIFI, WIFI_DISCONNECTED, ETHERNET, OTHER, NONE }
    enum class NetIcon { WIFI_0, WIFI_1, WIFI_2, WIFI_3, WIFI_OFF, ETHERNET, OFFLINE }

    enum class BtIcon { HIDDEN, ON, CONNECTED }

    /** Default-network transports to a kind; Ethernet wins over Wi-Fi when both are reported. */
    fun netKind(hasNetwork: Boolean, ethernet: Boolean, wifi: Boolean, wifiEnabled: Boolean): NetKind = when {
        !hasNetwork -> if (wifiEnabled) NetKind.WIFI_DISCONNECTED else NetKind.NONE
        ethernet -> NetKind.ETHERNET
        wifi -> NetKind.WIFI
        else -> NetKind.OTHER
    }

    /** Hidden unless an adapter exists and is on; CONNECTED when any audio/input device is linked. */
    fun bluetoothIcon(present: Boolean, enabled: Boolean, connected: Boolean): BtIcon = when {
        !present || !enabled -> BtIcon.HIDDEN
        connected -> BtIcon.CONNECTED
        else -> BtIcon.ON
    }

    const val LEVELS = 4
    private const val MIN_RSSI = -100
    private const val MAX_RSSI = -55

    /** Same buckets as WifiManager.calculateSignalLevel(rssi, 4): 0..3. */
    fun wifiLevel(rssi: Int): Int = when {
        rssi <= MIN_RSSI -> 0
        rssi >= MAX_RSSI -> LEVELS - 1
        else -> (rssi - MIN_RSSI) * (LEVELS - 1) / (MAX_RSSI - MIN_RSSI)
    }

    fun iconFor(kind: NetKind, level: Int): NetIcon = when (kind) {
        NetKind.WIFI -> when (level.coerceIn(0, LEVELS - 1)) {
            0 -> NetIcon.WIFI_0
            1 -> NetIcon.WIFI_1
            2 -> NetIcon.WIFI_2
            else -> NetIcon.WIFI_3
        }
        NetKind.WIFI_DISCONNECTED -> NetIcon.WIFI_OFF
        NetKind.ETHERNET, NetKind.OTHER -> NetIcon.ETHERNET
        NetKind.NONE -> NetIcon.OFFLINE
    }
}
