package dev.tvlauncher.devices

import dev.tvlauncher.R

object DeviceIcons {
    fun res(kind: DeviceKind): Int = when (kind) {
        DeviceKind.CONSOLE_PLAYSTATION, DeviceKind.CONSOLE_XBOX, DeviceKind.CONSOLE_NINTENDO -> R.drawable.ic_dev_console
        DeviceKind.GAMEPAD, DeviceKind.BT_GAMEPAD -> R.drawable.ic_dev_controller
        DeviceKind.STREAMER -> R.drawable.ic_dev_streamer
        DeviceKind.SET_TOP_BOX -> R.drawable.ic_dev_settop
        DeviceKind.BLURAY_DVD -> R.drawable.ic_dev_disc
        DeviceKind.SOUNDBAR_AUDIO -> R.drawable.ic_dev_soundbar
        DeviceKind.PC_LAPTOP -> R.drawable.ic_dev_pc
        DeviceKind.HDMI_OTHER -> R.drawable.ic_dev_hdmi
        DeviceKind.TUNER_ANTENNA -> R.drawable.ic_dev_antenna
        DeviceKind.COMPOSITE_AV -> R.drawable.ic_dev_av
        DeviceKind.COMPONENT, DeviceKind.VGA -> R.drawable.ic_dev_component
        DeviceKind.USB_STORAGE, DeviceKind.USB_HID_OTHER -> R.drawable.ic_dev_usb
        DeviceKind.KEYBOARD, DeviceKind.BT_KEYBOARD_MOUSE -> R.drawable.ic_dev_keyboard
        DeviceKind.MOUSE -> R.drawable.ic_dev_mouse
        DeviceKind.BT_HEADPHONES -> R.drawable.ic_dev_headphones
        DeviceKind.BT_SPEAKER -> R.drawable.ic_dev_speaker
        DeviceKind.PHONE_CAST -> R.drawable.ic_dev_phonecast
        DeviceKind.NETWORK_ETHERNET -> R.drawable.ic_dev_ethernet
        DeviceKind.NETWORK_WIFI -> R.drawable.ic_sc_wifi
        DeviceKind.BT_OTHER, DeviceKind.UNKNOWN -> R.drawable.ic_dev_generic
    }
}
