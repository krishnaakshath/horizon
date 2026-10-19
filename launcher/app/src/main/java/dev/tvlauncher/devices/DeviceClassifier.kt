package dev.tvlauncher.devices

import java.util.Locale

/** Pure mapping from platform identifiers (passed as plain ints/strings) to [DeviceKind]. */
object DeviceClassifier {
    // TvInputInfo.TYPE_*
    private const val TYPE_TUNER = 0
    private const val TYPE_COMPOSITE = 1001
    private const val TYPE_SVIDEO = 1002
    private const val TYPE_SCART = 1003
    private const val TYPE_COMPONENT = 1004
    private const val TYPE_VGA = 1005
    private const val TYPE_DVI = 1006
    private const val TYPE_HDMI = 1007
    private const val TYPE_DISPLAY_PORT = 1008

    // InputDevice.SOURCE_*
    private const val SRC_KEYBOARD = 0x101
    private const val SRC_GAMEPAD = 0x401
    private const val SRC_JOYSTICK = 0x1000010
    private const val SRC_MOUSE = 0x2002

    // BluetoothClass
    private const val MAJOR_AUDIO_VIDEO = 0x0400
    private const val MAJOR_PERIPHERAL = 0x0500
    private const val MINOR_HEADSET = 0x0404
    private const val MINOR_HANDSFREE = 0x0408
    private const val MINOR_LOUDSPEAKER = 0x0414
    private const val MINOR_HEADPHONES = 0x0418
    private const val MINOR_PORTABLE_AUDIO = 0x041C
    private const val MINOR_HIFI = 0x0428
    private const val PERIPHERAL_JOYSTICK = 0x0504
    private const val PERIPHERAL_GAMEPAD = 0x0508
    private const val PERIPHERAL_KB_MASK = 0x00C0

    /** All keyword regexes are built once, whole-word and case-insensitive. */
    private object Patterns {
        private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
        val playstation = rx("""\b(playstation\d*|ps5|ps4|ps3|ps vita|sony interactive entertainment)\b""")
        val xbox = rx("""\bxbox\d*\b""")
        val nintendo = rx("""\bnintendo\b""")
        val switchWord = rx("""\bswitch\b""")
        val notNintendoSwitch = rx("""\b(network|ethernet|hdmi|kvm|usb|poe|managed)\b""")
        val streamer = rx("""\b(fire ?tv|chromecast|apple ?tv|roku|nvidia shield|shield tv|mi box)\b""")
        val disc = rx("""\b(blu-?ray|bd player|dvd)\b""")
        val audio = rx("""\b(sound ?bar|audio system|receiver|avr|sonos|bose)\b""")
        val setTop = rx("""\b(set-?top|sky|stb|dish|tata)\b""")
        val pc = rx("""\b(pc|laptop|computer)\b""")
    }

    private fun String?.norm(): String = (this ?: "").trim().lowercase(Locale.ROOT)

    fun fromHdmiName(name: String?): DeviceKind {
        val n = name.norm()
        if (n.isEmpty()) return DeviceKind.HDMI_OTHER
        return when {
            Patterns.playstation.containsMatchIn(n) -> DeviceKind.CONSOLE_PLAYSTATION
            Patterns.xbox.containsMatchIn(n) -> DeviceKind.CONSOLE_XBOX
            Patterns.nintendo.containsMatchIn(n) ||
                (Patterns.switchWord.containsMatchIn(n) && !Patterns.notNintendoSwitch.containsMatchIn(n)) ->
                DeviceKind.CONSOLE_NINTENDO
            Patterns.streamer.containsMatchIn(n) -> DeviceKind.STREAMER
            Patterns.disc.containsMatchIn(n) -> DeviceKind.BLURAY_DVD
            Patterns.audio.containsMatchIn(n) -> DeviceKind.SOUNDBAR_AUDIO
            Patterns.setTop.containsMatchIn(n) -> DeviceKind.SET_TOP_BOX
            Patterns.pc.containsMatchIn(n) -> DeviceKind.PC_LAPTOP
            else -> DeviceKind.HDMI_OTHER
        }
    }

    fun fromTvInputType(type: Int, hdmiName: String?): DeviceKind = when (type) {
        TYPE_TUNER -> DeviceKind.TUNER_ANTENNA
        TYPE_COMPOSITE, TYPE_SVIDEO, TYPE_SCART -> DeviceKind.COMPOSITE_AV
        TYPE_COMPONENT -> DeviceKind.COMPONENT
        TYPE_VGA, TYPE_DVI -> DeviceKind.VGA
        TYPE_HDMI, TYPE_DISPLAY_PORT -> fromHdmiName(hdmiName)
        else -> DeviceKind.UNKNOWN
    }

    /** [deviceClass] is the full BluetoothClass.getDeviceClass() value (major bits included). */
    fun fromBluetooth(majorClass: Int, deviceClass: Int, name: String?): DeviceKind {
        val n = name.norm()
        if (listOf("dualsense", "dualshock", "wireless controller", "xbox").any { n.contains(it) }) return DeviceKind.BT_GAMEPAD
        if (majorClass == MAJOR_AUDIO_VIDEO) {
            return when (deviceClass) {
                MINOR_HEADSET, MINOR_HANDSFREE, MINOR_HEADPHONES -> DeviceKind.BT_HEADPHONES
                MINOR_LOUDSPEAKER, MINOR_PORTABLE_AUDIO, MINOR_HIFI -> DeviceKind.BT_SPEAKER
                else -> DeviceKind.BT_OTHER
            }
        }
        if (majorClass == MAJOR_PERIPHERAL) {
            if (deviceClass == PERIPHERAL_GAMEPAD || deviceClass == PERIPHERAL_JOYSTICK) return DeviceKind.BT_GAMEPAD
            if (deviceClass and PERIPHERAL_KB_MASK != 0) return DeviceKind.BT_KEYBOARD_MOUSE
        }
        return DeviceKind.BT_OTHER
    }

    /** Returns UNKNOWN for anything that should not trigger a card (remote, built-in, virtual). */
    fun fromInputDevice(sources: Int, name: String?, isExternal: Boolean, isVirtual: Boolean = false): DeviceKind {
        if (isVirtual) return DeviceKind.UNKNOWN
        if (RemoteFilter.isExplicitRemoteName(name)) return DeviceKind.UNKNOWN
        return when {
            sources and SRC_GAMEPAD == SRC_GAMEPAD || sources and SRC_JOYSTICK == SRC_JOYSTICK -> DeviceKind.GAMEPAD
            sources and SRC_MOUSE == SRC_MOUSE -> DeviceKind.MOUSE
            sources and SRC_KEYBOARD == SRC_KEYBOARD && isExternal -> DeviceKind.KEYBOARD
            else -> DeviceKind.UNKNOWN
        }
    }

    fun fromUsb(usbClass: Int, subclass: Int, name: String?): DeviceKind {
        val n = name.norm()
        return when (usbClass) {
            8 -> DeviceKind.USB_STORAGE
            3 -> when {
                n.contains("keyboard") -> DeviceKind.KEYBOARD
                n.contains("mouse") -> DeviceKind.MOUSE
                n.contains("gamepad") || n.contains("controller") || n.contains("joystick") -> DeviceKind.GAMEPAD
                else -> DeviceKind.USB_HID_OTHER
            }
            else -> DeviceKind.UNKNOWN
        }
    }
}
