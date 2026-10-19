package dev.tvlauncher.appsettings

import dev.tvlauncher.ui.StatusModel.NetKind

/** Pure status strings for the Network & system page. */
object StatusText {
    /** The SSID without its quotes, or null when the system hides it (no location permission). */
    fun ssid(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"") ?: return null
        return if (s.isEmpty() || s == "<unknown ssid>" || s == "0x") null else s
    }

    /** [level] is 0..3 as produced by StatusModel.wifiLevel. */
    fun wifi(kind: NetKind, ssid: String?, level: Int): String = when (kind) {
        NetKind.WIFI -> "${ssid ?: "Connected"} · Signal ${level.coerceIn(0, 3) + 1} of 4"
        NetKind.ETHERNET -> "Ethernet connected"
        NetKind.OTHER -> "Connected"
        NetKind.WIFI_DISCONNECTED -> "Not connected"
        NetKind.NONE -> "Off"
    }

    fun bluetooth(present: Boolean, enabled: Boolean, connected: Boolean): String = when {
        !present -> "Not available"
        !enabled -> "Off"
        connected -> "On · Device connected"
        else -> "On"
    }
}
