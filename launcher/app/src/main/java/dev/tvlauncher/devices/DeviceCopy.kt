package dev.tvlauncher.devices

import java.util.Locale

/** Card text. Never emits "null" or blank strings. */
object DeviceCopy {
    fun label(kind: DeviceKind): String = when (kind) {
        DeviceKind.CONSOLE_PLAYSTATION -> "PlayStation"
        DeviceKind.CONSOLE_XBOX -> "Xbox"
        DeviceKind.CONSOLE_NINTENDO -> "Nintendo"
        DeviceKind.STREAMER -> "Streaming device"
        DeviceKind.SET_TOP_BOX -> "Set-top box"
        DeviceKind.BLURAY_DVD -> "Disc player"
        DeviceKind.SOUNDBAR_AUDIO -> "Sound system"
        DeviceKind.PC_LAPTOP -> "Computer"
        DeviceKind.HDMI_OTHER -> "HDMI device"
        DeviceKind.TUNER_ANTENNA -> "Antenna"
        DeviceKind.COMPOSITE_AV -> "AV input"
        DeviceKind.COMPONENT -> "Component input"
        DeviceKind.VGA -> "VGA input"
        DeviceKind.USB_STORAGE -> "USB drive"
        DeviceKind.USB_HID_OTHER -> "USB device"
        DeviceKind.GAMEPAD, DeviceKind.BT_GAMEPAD -> "Controller"
        DeviceKind.KEYBOARD -> "Keyboard"
        DeviceKind.MOUSE -> "Mouse"
        DeviceKind.BT_HEADPHONES -> "Headphones"
        DeviceKind.BT_SPEAKER -> "Speaker"
        DeviceKind.BT_KEYBOARD_MOUSE -> "Keyboard or mouse"
        DeviceKind.BT_OTHER -> "Bluetooth device"
        DeviceKind.PHONE_CAST -> "Phone"
        DeviceKind.NETWORK_ETHERNET -> "Ethernet"
        DeviceKind.NETWORK_WIFI -> "Wi-Fi"
        DeviceKind.UNKNOWN -> "Device"
    }

    /** Subject of the title: a friendly canonical name for consoles, the device name where it helps. */
    fun subject(device: ConnectedDevice): String {
        val n = device.name.trim()
        val l = n.lowercase(Locale.ROOT)
        return when (device.kind) {
            DeviceKind.CONSOLE_PLAYSTATION -> when {
                l.contains("ps5") || l.contains("playstation 5") -> "PlayStation 5"
                l.contains("ps4") || l.contains("playstation 4") -> "PlayStation 4"
                l.contains("ps3") || l.contains("playstation 3") -> "PlayStation 3"
                else -> "PlayStation"
            }
            DeviceKind.CONSOLE_XBOX -> if (l.contains("xbox") && n.length <= 24) n else "Xbox"
            DeviceKind.CONSOLE_NINTENDO -> if (l.contains("switch")) "Nintendo Switch" else "Nintendo"
            DeviceKind.STREAMER, DeviceKind.SET_TOP_BOX, DeviceKind.BLURAY_DVD, DeviceKind.SOUNDBAR_AUDIO,
            DeviceKind.PC_LAPTOP, DeviceKind.HDMI_OTHER, DeviceKind.PHONE_CAST,
            DeviceKind.BT_OTHER, DeviceKind.UNKNOWN -> if (n.isNotEmpty()) n else label(device.kind)
            else -> label(device.kind)
        }
    }

    fun title(device: ConnectedDevice, connected: Boolean): String =
        subject(device) + if (connected) " connected" else " disconnected"

    fun subtitle(device: ConnectedDevice, connected: Boolean): String {
        val parts = ArrayList<String>()
        device.port?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        device.detail?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        if (parts.isEmpty()) {
            val n = device.name.trim()
            // Show the raw name when the title used a generic label.
            if (n.isNotEmpty() && subject(device) != n) parts.add(n)
        }
        if (parts.isEmpty()) parts.add(label(device.kind))
        return parts.joinToString(" · ")
    }
}
