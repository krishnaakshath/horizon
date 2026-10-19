package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HdmiPortTrackerTest {
    private var now = 0L
    private fun tracker() = HdmiPortTracker({ now })
    private fun port(id: String = "hw1", label: String = "HDMI 1", state: Int = 0) = InputSnapshot(id, null, label, 1007, state)
    private fun child(id: String = "cec1", parent: String = "hw1", label: String = "PlayStation 5", state: Int = 0) =
        InputSnapshot(id, parent, label, 1007, state)

    @Test fun portWithoutChild_genericName() {
        val t = tracker(); t.update(listOf(port()), emit = false)
        val d = t.devices().single()
        assertEquals("HDMI device", d.name); assertEquals("HDMI 1", d.port); assertEquals("tv:hw1", d.id)
    }

    @Test fun consolePowerOn_emitsRename_notConnectDisconnectPair() {
        val t = tracker(); t.update(listOf(port()), emit = false)
        val ev = t.update(listOf(port(), child()))
        assertEquals(1, ev.size)
        val u = ev.single() as DeviceEvent.Updated
        assertEquals("PlayStation 5", u.device.name)
        assertEquals(DeviceKind.CONSOLE_PLAYSTATION, u.device.kind)
        assertEquals("HDMI device", u.previous.name)
        assertEquals("tv:hw1", u.device.id)
    }

    @Test fun consolePowerOff_emitsAtMostOneEvent() {
        val t = tracker(); t.update(listOf(port(), child()), emit = false)
        val ev = t.update(listOf(port(), child(state = 2)))
        assertEquals(emptyList<DeviceEvent>(), ev)
        assertEquals("HDMI device", t.devices().single().name)
    }

    @Test fun flapWithin8s_swallowed() {
        val t = tracker(); t.update(listOf(port()), emit = false)
        now = 1000; val a = t.update(emptyList())
        now = 4000; val b = t.update(listOf(port()))
        now = 30000; val c = t.tick()
        assertTrue(a.isEmpty() && b.isEmpty() && c.isEmpty())
    }

    @Test fun sustainedDisconnect_announcedOnce() {
        val t = tracker(); t.update(listOf(port()), emit = false)
        now = 0; t.update(emptyList())
        now = 7999; assertTrue(t.tick().isEmpty())
        now = 8000; val ev = t.tick()
        assertEquals(1, ev.size); assertTrue(ev.single() is DeviceEvent.Disconnected)
        now = 20000; assertTrue(t.tick().isEmpty())
        assertNull(t.nextDueDelayMs())
    }

    @Test fun standby_isStillConnected() {
        val t = tracker(); t.update(listOf(port(), child()), emit = false)
        val ev = t.update(listOf(port(state = 1), child(state = 1)))
        assertTrue(ev.isEmpty())
        assertEquals("PlayStation 5", t.devices().single().name)
    }

    @Test fun twoPorts_independent() {
        val t = tracker()
        t.update(listOf(port("hw1", "HDMI 1"), port("hw2", "HDMI 2")), emit = false)
        now = 0; t.update(listOf(port("hw2", "HDMI 2")))
        now = 8000
        val ev = t.tick()
        assertEquals(listOf("tv:hw1"), ev.map { it.device.id })
        assertEquals(listOf("tv:hw2"), t.devices().map { it.id })
    }

    @Test fun apiBelow26_parentDerivedFromCecId() {
        val t = tracker()
        val cec = InputSnapshot("com.v/.Svc/HDMI100000", null, "Xbox", 1007, 0)
        t.update(listOf(port(), cec), emit = false)
        val d = t.devices().single()
        assertEquals("tv:hw1", d.id); assertEquals("Xbox", d.name); assertEquals(DeviceKind.CONSOLE_XBOX, d.kind)
    }

    @Test fun stopStart_resetsState() {
        val t = tracker()
        t.update(listOf(port(), child()), emit = false)
        now = 0; t.update(emptyList())
        t.reset()
        assertTrue(t.devices().isEmpty()); assertNull(t.nextDueDelayMs())
        now = 20000; assertTrue(t.tick().isEmpty())
        assertTrue(t.update(listOf(port()), emit = false).isEmpty())
        assertEquals("HDMI device", t.devices().single().name)
    }

    @Test fun swapConsolesWithin8s_announcesNewDevice() {
        val t = tracker(); t.update(listOf(port(), child()), emit = false)
        now = 0; t.update(emptyList())
        now = 3000
        val ev = t.update(listOf(port(), child(label = "Xbox Series X")))
        // Port reappears with a different console: one Updated, never a swallowed no-op.
        assertEquals(1, ev.size)
        val u = ev.single() as DeviceEvent.Updated
        assertEquals("Xbox Series X", u.device.name); assertEquals("PlayStation 5", u.previous.name)
    }

    @Test fun compositePort_keepsLabel() {
        val t = tracker()
        t.update(listOf(InputSnapshot("av1", null, "AV 1", 1001, 0)), emit = false)
        assertEquals(DeviceKind.COMPOSITE_AV, t.devices().single().kind)
        assertEquals("AV 1", t.devices().single().name)
    }
}

class InputIdParserTest {
    @Test fun cecChildIds() {
        assertEquals(1, InputIdParser.parentPortOf("com.vendor.tv/.hdmi.Svc/HDMI100000"))
        assertEquals(2, InputIdParser.parentPortOf("x.y/.Z#CEC2000"))
    }
    @Test fun portIdsAndGarbage_noParent() {
        assertNull(InputIdParser.parentPortOf("com.vendor.tv/.Svc/HW5"))
        assertNull(InputIdParser.parentPortOf("x/.Hdmi1InputService"))
        assertNull(InputIdParser.parentPortOf(null))
        assertNull(InputIdParser.parentPortOf(""))
    }
}

class FlapGuardTest {
    @Test fun connectedDifferentDevice_afterHeldDisconnect_isUpdated() {
        val g = FlapGuard(8000) { 0L }
        val a = ConnectedDevice("tv:1", "PS5", DeviceKind.CONSOLE_PLAYSTATION, null, null)
        val b = ConnectedDevice("tv:1", "Xbox", DeviceKind.CONSOLE_XBOX, null, null)
        g.submit(DeviceEvent.Disconnected(a))
        assertEquals(listOf<DeviceEvent>(DeviceEvent.Updated(b, a)), g.submit(DeviceEvent.Connected(b)))
    }

    @Test fun updatedWithSameName_cancelsHeld() {
        var now = 0L
        val g = FlapGuard(8000) { now }
        val a = ConnectedDevice("tv:1", "PS5", DeviceKind.CONSOLE_PLAYSTATION, null, null)
        g.submit(DeviceEvent.Disconnected(a))
        assertTrue(g.submit(DeviceEvent.Updated(a, a)).isEmpty())
    }
}
