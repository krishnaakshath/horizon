package dev.tvlauncher.devices

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
