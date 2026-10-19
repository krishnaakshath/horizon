package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSourceDedupeTest {
    private var now = 0L
    private fun dd() = CrossSourceDedupe(6000) { now }
    private fun c(name: String, kind: DeviceKind) = DeviceEvent.Connected(ConnectedDevice(name, name, kind, null, null))

    @Test fun btPad_thenInputManager_within6s_onePad() {
        val d = dd()
        assertTrue(d.accept("bt", c("DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
        now = 5000
        assertFalse(d.accept("input", c("DualSense Wireless Controller", DeviceKind.GAMEPAD)))
    }

    @Test fun usbHid_thenInputKeyboard_onePad() {
        val d = dd()
        assertTrue(d.accept("usb", c("Logitech USB Receiver", DeviceKind.USB_HID_OTHER)))
        now = 2000
        assertFalse(d.accept("input", c("Logitech USB Receiver Keyboard", DeviceKind.KEYBOARD)))
    }

    @Test fun sameNameLater_isSeparate() {
        val d = dd()
        assertTrue(d.accept("bt", c("DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)))
        now = 7000
        assertTrue(d.accept("input", c("DualSense Wireless Controller", DeviceKind.GAMEPAD)))
    }

    @Test fun unrelatedDevices_notMerged() {
        val d = dd()
        assertTrue(d.accept("bt", c("WH-1000XM4", DeviceKind.BT_HEADPHONES)))
        assertTrue(d.accept("usb", c("SanDisk Ultra", DeviceKind.USB_STORAGE)))
        assertTrue(d.accept("bt", c("Logitech K400", DeviceKind.BT_KEYBOARD_MOUSE)))
        assertTrue(d.accept("input", c("Apple Magic Keyboard", DeviceKind.KEYBOARD)))
    }

    @Test fun twoIdenticalGamepads_usbPlusBt_within6s_bothAnnounced_whenIdsDiffer() {
        val d = dd()
        val pad = { id: String, k: DeviceKind -> DeviceEvent.Connected(ConnectedDevice(id, "Wireless Gamepad Pro", k, null, null)) }
        val sent = ArrayList<String>()
        if (d.accept("usb", pad("usb:1", DeviceKind.GAMEPAD), "054c:0ce6")) sent.add("usb")
        if (d.accept("bt", pad("bt:AA", DeviceKind.BT_GAMEPAD))) sent.add("bt")
        // InputManager reports each pad once: both are consumed by their source record.
        if (d.accept("input", pad("input:a", DeviceKind.GAMEPAD), "054c:0ce6")) sent.add("input-a")
        if (d.accept("input", pad("input:b", DeviceKind.GAMEPAD), "054c:0ce6")) sent.add("input-b")
        assertEquals(listOf("usb", "bt"), sent)
    }

    @Test fun matchIsConsumed_thirdReportAnnounces() {
        val d = dd()
        assertTrue(d.accept("bt", c("Pad Alpha", DeviceKind.BT_GAMEPAD)))
        assertFalse(d.accept("input", c("Pad Alpha", DeviceKind.GAMEPAD)))
        assertTrue(d.accept("input", c("Pad Alpha", DeviceKind.GAMEPAD)))
    }

    @Test fun genericTokenlessNames_withoutIdsNotMerged() {
        val d = dd()
        assertTrue(d.accept("usb", c("Wireless Controller", DeviceKind.GAMEPAD)))
        assertTrue(d.accept("input", c("USB Gamepad", DeviceKind.GAMEPAD)))
    }

    @Test fun sameVidPid_merges() {
        val d = dd()
        assertTrue(d.accept("usb", c("Foo", DeviceKind.USB_HID_OTHER), "1234:5678"))
        assertFalse(d.accept("input", c("Bar", DeviceKind.KEYBOARD), "1234:5678"))
    }

    @Test fun zeroIds_neverMerge() {
        val d = dd()
        assertTrue(d.accept("usb", c("Foo", DeviceKind.USB_HID_OTHER), "0000:1234"))
        assertTrue(d.accept("input", c("Bar", DeviceKind.KEYBOARD), "0000:1234"))
    }

    @Test fun disconnectNotMergedWithConnect() {
        val d = dd()
        assertTrue(d.accept("bt", c("Pad X", DeviceKind.BT_GAMEPAD)))
        assertTrue(d.accept("input", DeviceEvent.Disconnected(ConnectedDevice("Pad X", "Pad X", DeviceKind.GAMEPAD, null, null))))
    }
}

class NetworkEventPolicyTest {
    private var now = 0L
    private fun dev(id: String, k: DeviceKind) = ConnectedDevice(id, id, k, null, null)
    private val wifi = dev("net:wifi", DeviceKind.NETWORK_WIFI)
    private val eth = dev("net:eth", DeviceKind.NETWORK_ETHERNET)

    @Test fun handover_sameBatch_swallowed() {
        val p = NetworkEventPolicy(3000) { now }
        assertTrue(p.submitAll(listOf(DeviceEvent.Connected(eth), DeviceEvent.Disconnected(wifi))).isEmpty())
    }

    @Test fun blip_swallowed() {
        val p = NetworkEventPolicy(3000) { now }
        assertTrue(p.submitAll(listOf(DeviceEvent.Disconnected(wifi))).isEmpty())
        now = 1500
        assertTrue(p.submitAll(listOf(DeviceEvent.Connected(wifi))).isEmpty())
        now = 10000
        assertTrue(p.tick().isEmpty())
    }

    @Test fun sustainedLoss_releasedOnce() {
        val p = NetworkEventPolicy(3000) { now }
        p.submitAll(listOf(DeviceEvent.Disconnected(wifi)))
        now = 3000
        assertTrue(p.tick().size == 1)
        assertTrue(p.tick().isEmpty())
    }

    @Test fun plainConnect_passes() {
        val p = NetworkEventPolicy(3000) { now }
        assertTrue(p.submitAll(listOf(DeviceEvent.Connected(eth))).size == 1)
    }
}
