package dev.tvlauncher.volume

/** Words and small decisions for Settings > Volume & output (pure, JVM-tested). */
object VolumeCopy {
    // android.media.AudioDeviceInfo TYPE_* values (stable platform constants).
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_LINE_ANALOG = 5
    const val TYPE_LINE_DIGITAL = 6
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_HDMI = 9
    const val TYPE_HDMI_ARC = 10
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_ACCESSORY = 12
    const val TYPE_DOCK = 13
    const val TYPE_FM = 14
    const val TYPE_BUILTIN_MIC = 15
    const val TYPE_TELEPHONY = 18
    const val TYPE_AUX_LINE = 19
    const val TYPE_IP = 20
    const val TYPE_BUS = 21
    const val TYPE_USB_HEADSET = 22
    const val TYPE_HEARING_AID = 23
    const val TYPE_BUILTIN_SPEAKER_SAFE = 24
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27
    const val TYPE_HDMI_EARC = 29

    /** A friendly name for an output type; null for internal ones that are not real outputs. */
    fun outputLabel(type: Int): String? = when (type) {
        TYPE_BUILTIN_SPEAKER, TYPE_BUILTIN_SPEAKER_SAFE -> "TV speakers"
        TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES, TYPE_USB_HEADSET -> "Headphones"
        TYPE_LINE_ANALOG, TYPE_AUX_LINE -> "Line out"
        TYPE_LINE_DIGITAL -> "Optical (S/PDIF)"
        TYPE_BLUETOOTH_A2DP, TYPE_BLE_SPEAKER -> "Bluetooth speaker"
        TYPE_BLE_HEADSET -> "Bluetooth headphones"
        TYPE_HEARING_AID -> "Hearing aid"
        TYPE_HDMI -> "HDMI"
        TYPE_HDMI_ARC -> "HDMI ARC sound system"
        TYPE_HDMI_EARC -> "HDMI eARC sound system"
        TYPE_USB_DEVICE, TYPE_USB_ACCESSORY -> "USB audio"
        TYPE_DOCK -> "Dock"
        else -> null // telephony, FM, bus, IP, mic...: not something to pick
    }

    /** Distinct friendly names, speakers first, in a stable order. */
    fun outputs(types: List<Int>): List<String> {
        val order = listOf(
            "TV speakers", "HDMI ARC sound system", "HDMI eARC sound system", "Optical (S/PDIF)", "Bluetooth speaker",
            "Bluetooth headphones", "Headphones", "Line out", "USB audio", "HDMI", "Hearing aid", "Dock"
        )
        return types.mapNotNull(::outputLabel).distinct().sortedBy { order.indexOf(it) }
    }

    /** Volume row value. */
    fun levelLabel(level: Int, muted: Boolean): String = if (muted) "Muted" else level.toString()
}

/** "Playing on" for the Output row (pure). */
object PlayingOn {
    fun label(route: Pair<String, Boolean>?, outputs: List<String>): String {
        if (route != null && !route.second && route.first.isNotBlank()) return route.first
        val arc = outputs.firstOrNull { it.startsWith("HDMI") && it.contains("ARC") }
        return when {
            arc != null -> "TV speakers or $arc"
            outputs.isNotEmpty() && "TV speakers" !in outputs -> outputs.first()
            else -> "TV speakers"
        }
    }
}
