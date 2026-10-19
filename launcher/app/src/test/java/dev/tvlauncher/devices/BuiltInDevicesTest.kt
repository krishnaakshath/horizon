package dev.tvlauncher.devices

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names from the owner's TV probe: the panel's own receivers and virtual devices never announce. */
class BuiltInDevicesTest {
    @Test fun ownersTvProbe_names_areInternal() {
        for (n in listOf("MStar Smart TV IR Receiver", "MTK TV KEYPAD", "gIrTouch_Mouse", "virtual-search", "sim-mouse", "sim-kb")) {
            assertTrue(n, BuiltInDevices.isTvInternal(n))
        }
    }

    @Test fun markers_matchAsWordsOrPrefixes() {
        for (n in listOf("IR Receiver", "ir_receiver", "Generic IR-Receiver 2", "TV keypad", "keypad", "gIrTouch",
            "girtouch_keyboard", "virtual", "Virtual_Keyboard", "virtual-keypad", "SIM-TOUCH", "sim_kb")) {
            assertTrue(n, BuiltInDevices.isTvInternal(n))
        }
    }

    @Test fun realAccessories_areNotInternal() {
        for (n in listOf("Logitech K400", "DualSense Wireless Controller", "Virtual Reality Headset", "Xbox Wireless Controller",
            "Simple Keyboard", "SimTech Mouse", "Simulator Wheel", "Keypadless Remote Keyboard", "Irish Keyboard",
            "Receiver", "IR Blaster Hub", "Sony WH-1000XM4", "", "   ")) {
            assertFalse(n, BuiltInDevices.isTvInternal(n))
        }
        assertFalse(BuiltInDevices.isTvInternal(null))
    }

    @Test fun remoteFilter_neverAnnouncesInternalDevices_anyKind() {
        val f = RemoteFilter { 0L }
        for (kind in listOf(DeviceKind.KEYBOARD, DeviceKind.MOUSE, DeviceKind.USB_HID_OTHER, DeviceKind.UNKNOWN, DeviceKind.GAMEPAD)) {
            for (n in listOf("MStar Smart TV IR Receiver", "MTK TV KEYPAD", "gIrTouch_Mouse", "virtual-search", "sim-mouse", "sim-kb")) {
                assertFalse("$n/$kind", f.shouldAnnounce(Candidate(DeviceSource.INPUT, "input:$n", n, kind), true))
                assertFalse("$n/$kind", f.shouldAnnounce(Candidate(DeviceSource.INPUT, "input:$n", n, kind), false))
            }
        }
        assertTrue(f.shouldAnnounce(Candidate(DeviceSource.INPUT, "input:vr", "Virtual Reality Headset", DeviceKind.UNKNOWN), true))
        assertTrue(f.shouldAnnounce(Candidate(DeviceSource.INPUT, "input:k400", "Logitech K400", DeviceKind.KEYBOARD), true))
    }
}
