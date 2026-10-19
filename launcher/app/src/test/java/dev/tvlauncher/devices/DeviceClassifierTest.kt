package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassifierTest {
    @Test fun ps5Name_isPlaystation() = assertEquals(DeviceKind.CONSOLE_PLAYSTATION, DeviceClassifier.fromHdmiName("PS5"))
    @Test fun playstationName_isPlaystation() = assertEquals(DeviceKind.CONSOLE_PLAYSTATION, DeviceClassifier.fromHdmiName("PlayStation 5"))
    @Test fun sonyInteractive_isPlaystation() =
        assertEquals(DeviceKind.CONSOLE_PLAYSTATION, DeviceClassifier.fromHdmiName("Sony Interactive Entertainment"))
    @Test fun xboxSeriesX_isXbox() = assertEquals(DeviceKind.CONSOLE_XBOX, DeviceClassifier.fromHdmiName("Xbox Series X"))
    @Test fun nintendo_isNintendo() = assertEquals(DeviceKind.CONSOLE_NINTENDO, DeviceClassifier.fromHdmiName("Nintendo Switch"))
    @Test fun fireTv_isStreamer() = assertEquals(DeviceKind.STREAMER, DeviceClassifier.fromHdmiName("Fire TV Stick"))
    @Test fun bluray_isDisc() = assertEquals(DeviceKind.BLURAY_DVD, DeviceClassifier.fromHdmiName("Blu-ray Player"))
    @Test fun soundbar_isAudio() = assertEquals(DeviceKind.SOUNDBAR_AUDIO, DeviceClassifier.fromHdmiName("Samsung Soundbar"))
    @Test fun dish_isSetTop() = assertEquals(DeviceKind.SET_TOP_BOX, DeviceClassifier.fromHdmiName("Dish Home"))
    @Test fun laptop_isPc() = assertEquals(DeviceKind.PC_LAPTOP, DeviceClassifier.fromHdmiName("My Laptop"))
    @Test fun shortKeyword_notMatchedInsideWord() = assertEquals(DeviceKind.HDMI_OTHER, DeviceClassifier.fromHdmiName("Epcot Display"))
    @Test fun nullName_isHdmiOther() = assertEquals(DeviceKind.HDMI_OTHER, DeviceClassifier.fromHdmiName(null))
    @Test fun emptyName_isHdmiOther() = assertEquals(DeviceKind.HDMI_OTHER, DeviceClassifier.fromHdmiName("  "))

    @Test fun tuner_isAntenna() = assertEquals(DeviceKind.TUNER_ANTENNA, DeviceClassifier.fromTvInputType(0, null))
    @Test fun composite_isAv() = assertEquals(DeviceKind.COMPOSITE_AV, DeviceClassifier.fromTvInputType(1001, null))
    @Test fun scart_isAv() = assertEquals(DeviceKind.COMPOSITE_AV, DeviceClassifier.fromTvInputType(1003, null))
    @Test fun component_isComponent() = assertEquals(DeviceKind.COMPONENT, DeviceClassifier.fromTvInputType(1004, null))
    @Test fun vga_isVga() = assertEquals(DeviceKind.VGA, DeviceClassifier.fromTvInputType(1005, null))
    @Test fun hdmiType_usesName() = assertEquals(DeviceKind.CONSOLE_XBOX, DeviceClassifier.fromTvInputType(1007, "Xbox"))
    @Test fun hdmiType_noName() = assertEquals(DeviceKind.HDMI_OTHER, DeviceClassifier.fromTvInputType(1007, null))
    @Test fun otherType_isUnknown() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromTvInputType(1000, null))

    @Test fun dualSenseName_isBtGamepad() = assertEquals(DeviceKind.BT_GAMEPAD, DeviceClassifier.fromBluetooth(0, 0, "DualSense Wireless Controller"))
    @Test fun headphonesMinor_isBtHeadphones() = assertEquals(DeviceKind.BT_HEADPHONES, DeviceClassifier.fromBluetooth(0x0400, 0x0418, "WH-1000"))
    @Test fun headsetMinor_isBtHeadphones() = assertEquals(DeviceKind.BT_HEADPHONES, DeviceClassifier.fromBluetooth(0x0400, 0x0404, null))
    @Test fun loudspeakerMinor_isBtSpeaker() = assertEquals(DeviceKind.BT_SPEAKER, DeviceClassifier.fromBluetooth(0x0400, 0x0414, "Boom"))
    @Test fun gamepadMinor_isBtGamepad() = assertEquals(DeviceKind.BT_GAMEPAD, DeviceClassifier.fromBluetooth(0x0500, 0x0508, null))
    @Test fun keyboardMinor_isBtKeyboard() = assertEquals(DeviceKind.BT_KEYBOARD_MOUSE, DeviceClassifier.fromBluetooth(0x0500, 0x0540, null))
    @Test fun pointingMinor_isBtKeyboard() = assertEquals(DeviceKind.BT_KEYBOARD_MOUSE, DeviceClassifier.fromBluetooth(0x0500, 0x0580, null))
    @Test fun unknownBt_isOther() = assertEquals(DeviceKind.BT_OTHER, DeviceClassifier.fromBluetooth(0x0100, 0x0100, "Thing"))

    @Test fun gamepadSource_isGamepad() = assertEquals(DeviceKind.GAMEPAD, DeviceClassifier.fromInputDevice(0x401, "Pad", true))
    @Test fun joystickSource_isGamepad() = assertEquals(DeviceKind.GAMEPAD, DeviceClassifier.fromInputDevice(0x1000010, "Stick", true))
    @Test fun externalKeyboard_isKeyboard() = assertEquals(DeviceKind.KEYBOARD, DeviceClassifier.fromInputDevice(0x101, "Logitech K400", true))
    @Test fun internalKeyboard_ignored() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromInputDevice(0x101, "gpio-keys", false))
    @Test fun mouse_isMouse() = assertEquals(DeviceKind.MOUSE, DeviceClassifier.fromInputDevice(0x2002, "USB Mouse", true))
    @Test fun remote_ignored() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromInputDevice(0x101, "Amlogic_Remote", true))
    @Test fun voiceRemote_ignored() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromInputDevice(0x101, "Voice Control", true))
    @Test fun rcu_ignored() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromInputDevice(0x101, "RCU Device", true))
    @Test fun remoteKeyboard_kept() = assertEquals(DeviceKind.KEYBOARD, DeviceClassifier.fromInputDevice(0x101, "Remote Keyboard", true))
    @Test fun virtual_ignored() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromInputDevice(0x401, "Virtual", true, isVirtual = true))

    @Test fun usbClass8_isStorage() = assertEquals(DeviceKind.USB_STORAGE, DeviceClassifier.fromUsb(8, 6, "Flash"))
    @Test fun usbHidKeyboard_byName() = assertEquals(DeviceKind.KEYBOARD, DeviceClassifier.fromUsb(3, 1, "USB Keyboard"))
    @Test fun usbHidMouse_byName() = assertEquals(DeviceKind.MOUSE, DeviceClassifier.fromUsb(3, 1, "Optical Mouse"))
    @Test fun usbHidGamepad_byName() = assertEquals(DeviceKind.GAMEPAD, DeviceClassifier.fromUsb(3, 0, "Game Controller"))
    @Test fun usbHidOther() = assertEquals(DeviceKind.USB_HID_OTHER, DeviceClassifier.fromUsb(3, 0, "Dongle"))
    @Test fun usbHub_unknown() = assertEquals(DeviceKind.UNKNOWN, DeviceClassifier.fromUsb(9, 0, null))

    private fun hdmi(n: String) = DeviceClassifier.fromHdmiName(n)
    @Test fun falsePositives_pinned() {
        for (n in listOf("Skylight", "Dishwasher", "Capcom", "Network Switch", "Spectrum", "Windshield",
            "Microsoft Surface", "Epcot Display", "Tatami", "PCB tester", "HDMI Switch", "Bosework"))
            assertEquals(n, DeviceKind.HDMI_OTHER, hdmi(n))
    }
    @Test fun positives_table() {
        assertEquals(DeviceKind.CONSOLE_PLAYSTATION, hdmi("PlayStation 5"))
        assertEquals(DeviceKind.CONSOLE_PLAYSTATION, hdmi("PS5"))
        assertEquals(DeviceKind.CONSOLE_PLAYSTATION, hdmi("Sony Interactive Entertainment"))
        assertEquals(DeviceKind.CONSOLE_XBOX, hdmi("Xbox Series X"))
        assertEquals(DeviceKind.CONSOLE_NINTENDO, hdmi("Nintendo Switch"))
        assertEquals(DeviceKind.CONSOLE_NINTENDO, hdmi("Switch"))
        assertEquals(DeviceKind.STREAMER, hdmi("NVIDIA SHIELD"))
        assertEquals(DeviceKind.STREAMER, hdmi("Fire TV Stick"))
        assertEquals(DeviceKind.STREAMER, hdmi("Chromecast"))
        assertEquals(DeviceKind.STREAMER, hdmi("Apple TV 4K"))
        assertEquals(DeviceKind.STREAMER, hdmi("Mi Box S"))
        assertEquals(DeviceKind.SOUNDBAR_AUDIO, hdmi("Denon AVR-X1700H"))
        assertEquals(DeviceKind.SET_TOP_BOX, hdmi("Sky Q"))
        assertEquals(DeviceKind.BLURAY_DVD, hdmi("Samsung Blu-ray"))
    }
}
