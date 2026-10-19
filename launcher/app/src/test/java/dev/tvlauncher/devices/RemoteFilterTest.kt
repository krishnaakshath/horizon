package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteFilterTest {
    private fun c(src: DeviceSource, key: String, name: String?, kind: DeviceKind, anon: Boolean = false) =
        Candidate(src, key, name, kind, anon)

    @Test fun seededBondedRemote_reconnect_silent() {
        val f = RemoteFilter(); f.seed("bt:AA")
        assertEquals(false, f.shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:AA", "Mi Remote", DeviceKind.BT_OTHER)))
        assertEquals(false, f.shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:AA", "Some Clicker", DeviceKind.BT_OTHER)))
    }

    @Test fun newlyBondedHeadphones_announced() =
        assertEquals(true, RemoteFilter().shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:BB", "WH-1000XM4", DeviceKind.BT_HEADPHONES)))

    @Test fun seededHeadphones_reconnect_announced() {
        val f = RemoteFilter(); f.seed("bt:BB")
        assertEquals(true, f.shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:BB", "WH-1000XM4", DeviceKind.BT_HEADPHONES)))
    }

    @Test fun nullClassTclRemote_silent() =
        assertEquals(false, RemoteFilter().shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:CC", "TCL RC802V", DeviceKind.BT_OTHER)))

    @Test fun anonymousBt_silent() =
        assertEquals(false, RemoteFilter().shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:DD", "Bluetooth device", DeviceKind.BT_OTHER, true)))

    @Test fun dualSenseNewlyConnected_announced() =
        assertEquals(true, RemoteFilter().shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:EE", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))

    @Test fun logitechKeyboardNewlyBonded_announced() =
        assertEquals(true, RemoteFilter().shouldAnnounce(c(DeviceSource.BLUETOOTH, "bt:FF", "Logitech K400", DeviceKind.BT_KEYBOARD_MOUSE)))

    @Test fun seededKeyboard_silent() {
        val f = RemoteFilter(); f.seed("input:k")
        assertEquals(false, f.shouldAnnounce(c(DeviceSource.INPUT, "input:k", "Logitech K400", DeviceKind.KEYBOARD)))
    }

    @Test fun newKeyboard_announced() =
        assertEquals(true, RemoteFilter().shouldAnnounce(c(DeviceSource.INPUT, "input:k2", "Logitech K400", DeviceKind.KEYBOARD)))

    @Test fun nameTable() {
        for (n in listOf("RC", "Mi BT", "Mi BT Remote", "Voice Control", "Google Assistant Remote", "IR Receiver", "RCU Device", "Hisense Pad"))
            assertEquals(n, false, RemoteFilter().shouldAnnounce(c(DeviceSource.INPUT, "input:$n", n, DeviceKind.MOUSE)))
    }

    @Test fun remoteWithKeyboardWord_kept() =
        assertEquals(true, RemoteFilter().shouldAnnounce(c(DeviceSource.INPUT, "input:rk", "Remote Keyboard", DeviceKind.KEYBOARD)))

    @Test fun shieldTvController_notFilteredAsTv() =
        assertEquals(true, RemoteFilter().shouldAnnounce(c(DeviceSource.INPUT, "input:s", "SHIELD TV Controller", DeviceKind.GAMEPAD)))
}
