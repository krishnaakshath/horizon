package dev.tvlauncher.devices

/** Accent colours per kind: plain hex values only, no logos or brand artwork. */
object DeviceAccent {
    const val PLAYSTATION = 0xFF0070D1.toInt()
    const val XBOX = 0xFF107C10.toInt()
    const val NINTENDO = 0xFFE60012.toInt()
    const val STREAMER = 0xFF8B5CF6.toInt()
    const val USB = 0xFFF5A524.toInt()
    const val CONTROLLER = 0xFF14B8A6.toInt()
    const val AUDIO = 0xFFA855F7.toInt()
    const val SLATE = 0xFF64748B.toInt()
    const val NETWORK = 0xFF38BDF8.toInt()

    fun of(kind: DeviceKind): Int = when (kind) {
        DeviceKind.CONSOLE_PLAYSTATION -> PLAYSTATION
        DeviceKind.CONSOLE_XBOX -> XBOX
        DeviceKind.CONSOLE_NINTENDO -> NINTENDO
        DeviceKind.STREAMER, DeviceKind.SET_TOP_BOX -> STREAMER
        DeviceKind.USB_STORAGE, DeviceKind.USB_HID_OTHER -> USB
        DeviceKind.GAMEPAD, DeviceKind.BT_GAMEPAD, DeviceKind.KEYBOARD, DeviceKind.MOUSE,
        DeviceKind.BT_KEYBOARD_MOUSE -> CONTROLLER
        DeviceKind.SOUNDBAR_AUDIO, DeviceKind.BT_HEADPHONES, DeviceKind.BT_SPEAKER -> AUDIO
        DeviceKind.NETWORK_ETHERNET, DeviceKind.NETWORK_WIFI, DeviceKind.PHONE_CAST -> NETWORK
        else -> SLATE
    }

    fun isConsole(kind: DeviceKind) = kind == DeviceKind.CONSOLE_PLAYSTATION ||
        kind == DeviceKind.CONSOLE_XBOX || kind == DeviceKind.CONSOLE_NINTENDO
}
