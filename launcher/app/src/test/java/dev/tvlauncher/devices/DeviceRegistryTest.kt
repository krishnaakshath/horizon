package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryTest {
    private fun dev(id: String, name: String = id, kind: DeviceKind = DeviceKind.HDMI_OTHER) =
        ConnectedDevice(id, name, kind, null, null)

    @Test fun newDevice_emitsConnected() {
        val d = dev("a")
        assertEquals(listOf<DeviceEvent>(DeviceEvent.Connected(d)), DeviceRegistry.diff(emptyList(), listOf(d)))
    }

    @Test fun removedDevice_emitsDisconnected() {
        val d = dev("a")
        assertEquals(listOf<DeviceEvent>(DeviceEvent.Disconnected(d)), DeviceRegistry.diff(listOf(d), emptyList()))
    }

    @Test fun unchanged_emitsNothing() {
        val d = dev("a")
        assertTrue(DeviceRegistry.diff(listOf(d), listOf(d)).isEmpty())
    }

    @Test fun renamedSameId_notDuplicated() {
        assertTrue(DeviceRegistry.diff(listOf(dev("a", "Old")), listOf(dev("a", "New"))).isEmpty())
    }

    @Test fun snapshot_sortedByKindPriorityThenName() {
        val r = DeviceRegistry()
        r.applyConnected(dev("1", "Zed Drive", DeviceKind.USB_STORAGE))
        r.applyConnected(dev("2", "Pad", DeviceKind.GAMEPAD))
        r.applyConnected(dev("3", "Beta Box", DeviceKind.STREAMER))
        r.applyConnected(dev("4", "PS5", DeviceKind.CONSOLE_PLAYSTATION))
        r.applyConnected(dev("5", "Alpha Drive", DeviceKind.USB_STORAGE))
        r.applyConnected(dev("6", "Bar", DeviceKind.SOUNDBAR_AUDIO))
        assertEquals(listOf("4", "3", "6", "5", "1", "2"), r.snapshot().map { it.id })
    }

    @Test fun applyDisconnected_removes() {
        val r = DeviceRegistry()
        r.applyConnected(dev("1"))
        r.applyDisconnected("1")
        assertTrue(r.snapshot().isEmpty())
    }

    @Test fun applyConnected_sameIdReplaces() {
        val r = DeviceRegistry()
        r.applyConnected(dev("1", "A")); r.applyConnected(dev("1", "B"))
        assertEquals(listOf("B"), r.snapshot().map { it.name })
    }

    @Test fun debouncer_suppressesDuplicateWithinGap() {
        var now = 0L
        val deb = Debouncer(1500) { now }
        val e = DeviceEvent.Connected(dev("a"))
        assertTrue(deb.accept(e))
        now = 1000
        assertFalse(deb.accept(e))
        now = 1600
        assertTrue(deb.accept(e))
    }

    @Test fun debouncer_differentIdOrTypePasses() {
        var now = 0L
        val deb = Debouncer(1500) { now }
        assertTrue(deb.accept(DeviceEvent.Connected(dev("a"))))
        assertTrue(deb.accept(DeviceEvent.Connected(dev("b"))))
        assertTrue(deb.accept(DeviceEvent.Disconnected(dev("a"))))
    }
}
