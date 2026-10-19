package dev.tvlauncher.devices

enum class DeviceKind {
    CONSOLE_PLAYSTATION, CONSOLE_XBOX, CONSOLE_NINTENDO,
    STREAMER, SET_TOP_BOX, BLURAY_DVD, SOUNDBAR_AUDIO, PC_LAPTOP, HDMI_OTHER,
    TUNER_ANTENNA, COMPOSITE_AV, COMPONENT, VGA,
    USB_STORAGE, USB_HID_OTHER, GAMEPAD, KEYBOARD, MOUSE,
    BT_HEADPHONES, BT_SPEAKER, BT_GAMEPAD, BT_KEYBOARD_MOUSE, BT_OTHER,
    PHONE_CAST, NETWORK_ETHERNET, NETWORK_WIFI, UNKNOWN
}

data class ConnectedDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val port: String?,
    val detail: String?
)

sealed class DeviceEvent {
    abstract val device: ConnectedDevice
    data class Connected(override val device: ConnectedDevice) : DeviceEvent()
    data class Disconnected(override val device: ConnectedDevice) : DeviceEvent()
}

/** Coarse grouping used for ordering and for the Devices panel sections. */
enum class DeviceGroup(val title: String) {
    CONSOLES("Game consoles"),
    STREAMING("Streaming and TV boxes"),
    AUDIO("Audio"),
    STORAGE("Storage"),
    CONTROLLERS("Controllers and input"),
    NETWORK("Network"),
    OTHER("Other")
}

fun DeviceKind.group(): DeviceGroup = when (this) {
    DeviceKind.CONSOLE_PLAYSTATION, DeviceKind.CONSOLE_XBOX, DeviceKind.CONSOLE_NINTENDO -> DeviceGroup.CONSOLES
    DeviceKind.STREAMER, DeviceKind.SET_TOP_BOX, DeviceKind.BLURAY_DVD, DeviceKind.PC_LAPTOP -> DeviceGroup.STREAMING
    DeviceKind.SOUNDBAR_AUDIO, DeviceKind.BT_HEADPHONES, DeviceKind.BT_SPEAKER -> DeviceGroup.AUDIO
    DeviceKind.USB_STORAGE -> DeviceGroup.STORAGE
    DeviceKind.GAMEPAD, DeviceKind.KEYBOARD, DeviceKind.MOUSE, DeviceKind.BT_GAMEPAD,
    DeviceKind.BT_KEYBOARD_MOUSE, DeviceKind.USB_HID_OTHER -> DeviceGroup.CONTROLLERS
    DeviceKind.NETWORK_ETHERNET, DeviceKind.NETWORK_WIFI -> DeviceGroup.NETWORK
    else -> DeviceGroup.OTHER
}
