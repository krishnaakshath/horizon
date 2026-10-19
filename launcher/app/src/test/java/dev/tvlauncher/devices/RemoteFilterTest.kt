package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteFilterTest {
    private var now = 0L
    private fun f() = RemoteFilter { now }
    private fun c(src: DeviceSource, key: String, name: String?, kind: DeviceKind, anon: Boolean = false, vp: String? = null) =
        Candidate(src, key, name, kind, anon, vp)
    private fun usb(name: String, kind: DeviceKind) = c(DeviceSource.USB, "usb:$name", name, kind)
    private fun bt(key: String, name: String, kind: DeviceKind) = c(DeviceSource.BLUETOOTH, key, name, kind)
    private fun input(key: String, name: String, kind: DeviceKind, vp: String? = null) = c(DeviceSource.INPUT, key, name, kind, false, vp)
    private val min = 60_000L

    // ---- scoping: name rules never touch non-HID kinds
    @Test fun usbStorage_namedTCL32GB_isAnnounced() = assertEquals(true, f().shouldAnnounce(usb("TCL 32GB", DeviceKind.USB_STORAGE)))
    @Test fun usbStorage_namedVoiceRecorder_isAnnounced() = assertEquals(true, f().shouldAnnounce(usb("Voice Recorder", DeviceKind.USB_STORAGE)))
    @Test fun usbStorage_namedVoicemod_isAnnounced() = assertEquals(true, f().shouldAnnounce(usb("Voicemod", DeviceKind.USB_STORAGE)))
    @Test fun phone_XiaomiMiPhone_isAnnounced() = assertEquals(true, f().shouldAnnounce(usb("Xiaomi Mi Phone", DeviceKind.UNKNOWN)))
    @Test fun hidDevice_namedTCLRC802V_isSilent() {
        assertEquals(false, f().shouldAnnounce(usb("TCL RC802V", DeviceKind.USB_HID_OTHER)))
        assertEquals(false, f().shouldAnnounce(bt("bt:1", "TCL RC802V", DeviceKind.BT_OTHER)))
    }
    @Test fun btSoundbar_namedTCL_isAnnounced() = assertEquals(true, f().shouldAnnounce(bt("bt:2", "TCL Soundbar", DeviceKind.BT_SPEAKER)))
    @Test fun console_namedTCLHDMI_isAnnounced() =
        assertEquals(true, f().shouldAnnounce(c(DeviceSource.TV_INPUT, "tv:1", "TCL HDMI", DeviceKind.CONSOLE_PLAYSTATION)))
    @Test fun controller_namedController_isAnnounced() = assertEquals(true, f().shouldAnnounce(input("input:p", "Controller", DeviceKind.GAMEPAD)))
    @Test fun mirrorCast_isAnnounced() = assertEquals(true, f().shouldAnnounce(c(DeviceSource.NETWORK, "cast:1", "TV Cast Remote", DeviceKind.PHONE_CAST)))
    @Test fun fireTvStick_isAnnounced() =
        assertEquals(true, f().shouldAnnounce(c(DeviceSource.TV_INPUT, "tv:2", "Fire TV Stick", DeviceKind.STREAMER)))

    @Test fun keyboard_namedRemoteKeyboard_announcedUnlessSeeded() {
        assertEquals(true, f().shouldAnnounce(input("input:rk", "Remote Keyboard", DeviceKind.KEYBOARD)))
        val s = f(); s.seed("input:rk", null, DeviceKind.KEYBOARD)
        assertEquals(false, s.shouldAnnounce(input("input:rk", "Remote Keyboard", DeviceKind.KEYBOARD)))
    }

    // ---- name table for HID kinds (whole words)
    @Test fun hostileNames_hid_silent() {
        for (n in listOf("RC", "Mi BT", "Mi BT Remote", "Voice Control", "Google Assistant Remote", "IR Receiver",
            "RCU Device", "Hisense Pad", "Amlogic_Remote", "TCL RC802V", "Xiaomi Remote"))
            assertEquals(n, false, f().shouldAnnounce(input("input:$n", n, DeviceKind.MOUSE)))
    }
    @Test fun hostileNames_hid_notRemotes_announced() {
        for (n in listOf("Voicemod Mic Controller", "Ircam Mouse", "Logitech K400", "Microsoft Ergonomic Mouse", "Rcade Stick"))
            assertEquals(n, true, f().shouldAnnounce(input("input:$n", n, DeviceKind.MOUSE)))
    }
    @Test fun hostileNames_nonHid_alwaysAnnounced() {
        for (n in listOf("Voice Recorder", "TV Backup", "Remote Backup", "Assistant Drive", "RCU Logs", "IR Camera", "Xiaomi 64GB"))
            assertEquals(n, true, f().shouldAnnounce(usb(n, DeviceKind.USB_STORAGE)))
    }

    // ---- seeded / flaps
    @Test fun seededBondedRemote_reconnect_silent() {
        val s = f(); s.seed("bt:AA", null, DeviceKind.BT_OTHER)
        assertEquals(false, s.shouldAnnounce(bt("bt:AA", "Some Clicker", DeviceKind.BT_OTHER)))
        assertEquals(false, s.shouldAnnounce(bt("bt:AA", "Some Clicker", DeviceKind.BT_OTHER), connected = false))
    }
    @Test fun newlyBondedHeadphones_announced() = assertEquals(true, f().shouldAnnounce(bt("bt:BB", "WH-1000XM4", DeviceKind.BT_HEADPHONES)))
    @Test fun anonymousBt_silent() = assertEquals(false, f().shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:DD", "Bluetooth device", DeviceKind.BT_OTHER, true)))
    @Test fun logitechKeyboardNewlyBonded_announced() = assertEquals(true, f().shouldAnnounce(bt("bt:FF", "Logitech K400", DeviceKind.BT_KEYBOARD_MOUSE)))
    @Test fun seededKeyboard_silentBothWays() {
        val s = f(); s.seed("input:k", null, DeviceKind.KEYBOARD)
        assertEquals(false, s.shouldAnnounce(input("input:k", "Logitech K400", DeviceKind.KEYBOARD)))
        assertEquals(false, s.shouldAnnounce(input("input:k", "Logitech K400", DeviceKind.KEYBOARD), connected = false))
    }
    @Test fun seededUsbStorage_removalAnnounced() {
        val s = f(); s.seed("usb:d", null, DeviceKind.USB_STORAGE)
        assertEquals(true, s.shouldAnnounce(usb("d", DeviceKind.USB_STORAGE).copy(key = "usb:d"), connected = false))
    }
    @Test fun seededBtPad_disconnect_silent() {
        val s = f(); s.seed("bt:P", null, DeviceKind.BT_GAMEPAD)
        now = 5 * min
        assertEquals(false, s.shouldAnnounce(bt("bt:P", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD), connected = false))
    }
    @Test fun seededBtPad_reconnect_silent() {
        val s = f(); s.seed("bt:P", null, DeviceKind.BT_GAMEPAD)
        now = 5 * min
        assertEquals(false, s.shouldAnnounce(bt("bt:P", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
    }
    @Test fun seededHeadphones_reconnectWithin30min_silent() {
        val s = f(); s.seed("bt:H", null, DeviceKind.BT_HEADPHONES)
        now = 10 * min
        assertEquals(false, s.shouldAnnounce(bt("bt:H", "WH-1000XM4", DeviceKind.BT_HEADPHONES)))
    }
    @Test fun newBtPad_connectAnnounced_thenSleepWake_silent_within30min() {
        val s = f()
        assertEquals(true, s.shouldAnnounce(bt("bt:N", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
        now = 10 * min
        assertEquals(false, s.shouldAnnounce(bt("bt:N", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD), connected = false))
        now = 20 * min
        assertEquals(false, s.shouldAnnounce(bt("bt:N", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
    }
    @Test fun sameBtPad_afterSixtyMinutes_announcesAgain() {
        val s = f()
        assertEquals(true, s.shouldAnnounce(bt("bt:N", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
        now = 1 * min
        s.shouldAnnounce(bt("bt:N", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD), connected = false)
        now = 61 * min
        assertEquals(true, s.shouldAnnounce(bt("bt:N", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
    }
    @Test fun dualSenseNewlyConnected_announced() =
        assertEquals(true, f().shouldAnnounce(bt("bt:EE", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))

    // ---- vendor:product
    @Test fun seededHidVidPid_silencesRenamedReenumeration() {
        val s = f(); s.seed("input:old", "2717:32b9", DeviceKind.KEYBOARD)
        assertEquals(false, s.shouldAnnounce(input("input:new", "Generic Device", DeviceKind.KEYBOARD, "2717:32b9")))
        assertEquals(true, s.shouldAnnounce(input("input:other", "Generic Device", DeviceKind.KEYBOARD, "046d:c52b")))
    }
    @Test fun seededStorageVidPid_doesNotSilenceAnything() {
        val s = f(); s.seed("usb:d", "0781:5581", DeviceKind.USB_STORAGE)
        assertEquals(true, s.shouldAnnounce(c(DeviceSource.USB, "usb:e", "Same Model Drive", DeviceKind.USB_STORAGE, false, "0781:5581")))
    }
}
