package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToastQueueTest {
    private fun dev(id: String) = ConnectedDevice(id, id, DeviceKind.HDMI_OTHER, null, null)
    private fun c(id: String) = DeviceEvent.Connected(dev(id))
    private fun d(id: String) = DeviceEvent.Disconnected(dev(id))

    @Test fun firstOffer_isQueuedThenAdvanced() {
        val q = ToastQueue()
        assertEquals(ToastQueue.Offer.QUEUED, q.offer(c("a")))
        assertEquals(c("a"), q.advance())
        assertEquals(c("a"), q.current)
    }

    @Test fun sameEventAsCurrent_ignored() {
        val q = ToastQueue(); q.offer(c("a")); q.advance()
        assertEquals(ToastQueue.Offer.IGNORED, q.offer(c("a")))
        assertNull(q.advance())
    }

    @Test fun oppositeEventForCurrent_preempts() {
        val q = ToastQueue(); q.offer(c("a")); q.advance()
        assertEquals(ToastQueue.Offer.PREEMPT, q.offer(d("a")))
        assertEquals(d("a"), q.advance())
    }

    @Test fun pendingSameDevice_coalesced() {
        val q = ToastQueue(); q.offer(c("a")); q.advance()
        q.offer(c("b")); q.offer(d("b"))
        assertEquals(1, q.pendingSize)
        assertEquals(d("b"), q.advance())
    }

    @Test fun cappedAtThree_dropsOldest() {
        val q = ToastQueue(); q.offer(c("0")); q.advance()
        for (i in 1..5) q.offer(c("$i"))
        assertEquals(3, q.pendingSize)
        assertEquals(c("3"), q.advance())
    }
}

class DeviceAccentTest {
    @Test fun consoleAccents_matchSpec() {
        assertEquals(0xFF0070D1.toInt(), DeviceAccent.of(DeviceKind.CONSOLE_PLAYSTATION))
        assertEquals(0xFF107C10.toInt(), DeviceAccent.of(DeviceKind.CONSOLE_XBOX))
        assertEquals(0xFFE60012.toInt(), DeviceAccent.of(DeviceKind.CONSOLE_NINTENDO))
    }

    @Test fun everyKindHasOpaqueAccent_andFallbacksAreExplicit() {
        val slateKinds = setOf(DeviceKind.HDMI_OTHER, DeviceKind.BLURAY_DVD, DeviceKind.PC_LAPTOP, DeviceKind.TUNER_ANTENNA,
            DeviceKind.COMPOSITE_AV, DeviceKind.COMPONENT, DeviceKind.VGA, DeviceKind.BT_OTHER, DeviceKind.UNKNOWN)
        for (k in DeviceKind.values()) {
            val a = DeviceAccent.of(k)
            assertEquals(k.name, 0xFF, (a ushr 24) and 0xFF)
            assertEquals(k.name, k in slateKinds, a == DeviceAccent.SLATE)
        }
        val families = listOf(DeviceAccent.PLAYSTATION, DeviceAccent.XBOX, DeviceAccent.NINTENDO, DeviceAccent.STREAMER,
            DeviceAccent.USB, DeviceAccent.CONTROLLER, DeviceAccent.AUDIO, DeviceAccent.NETWORK, DeviceAccent.SLATE)
        assertEquals(families.size, families.toSet().size)
    }
}
