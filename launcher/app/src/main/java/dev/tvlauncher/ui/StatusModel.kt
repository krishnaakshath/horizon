package dev.tvlauncher.ui

/** Pure status-bar rules: Wi-Fi level buckets and which icon represents the network. */
object StatusModel {
    enum class NetKind { WIFI, WIFI_DISCONNECTED, ETHERNET, OTHER, NONE }
    enum class NetIcon { WIFI_0, WIFI_1, WIFI_2, WIFI_3, WIFI_OFF, ETHERNET, OFFLINE }

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

    fun showBluetooth(present: Boolean, enabled: Boolean) = present && enabled
}
