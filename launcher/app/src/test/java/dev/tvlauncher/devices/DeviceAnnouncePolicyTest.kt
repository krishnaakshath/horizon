package dev.tvlauncher.devices

import dev.tvlauncher.devices.DeviceAnnouncePolicy.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAnnouncePolicyTest {
    private fun dev(kind: DeviceKind, id: String = "x:" + kind.name, name: String = "Thing") = ConnectedDevice(id, name, kind, null, null)
    private fun on(kind: DeviceKind) = DeviceEvent.Connected(dev(kind))
    private fun off(kind: DeviceKind) = DeviceEvent.Disconnected(dev(kind))
    private val late = 60_000L

    @Test fun nonNetworkKinds_alwaysShowCards() {
        for (k in DeviceKind.values().filter { it != DeviceKind.NETWORK_WIFI && it != DeviceKind.NETWORK_ETHERNET }) {
            assertTrue(k.name, DeviceAnnouncePolicy.showCard(on(k), late, splashRunning = false))
            assertTrue(k.name, DeviceAnnouncePolicy.showCard(off(k), late, splashRunning = false))
        }
    }

    @Test fun ethernet_plugAndUnplugOnly() {
        assertTrue(DeviceAnnouncePolicy.showCard(on(DeviceKind.NETWORK_ETHERNET), late, false))
        assertTrue(DeviceAnnouncePolicy.showCard(off(DeviceKind.NETWORK_ETHERNET), late, false))
        val upd = DeviceEvent.Updated(dev(DeviceKind.NETWORK_ETHERNET), dev(DeviceKind.NETWORK_ETHERNET))
        assertFalse(DeviceAnnouncePolicy.showCard(upd, late, false))
    }

    @Test fun wifi_neverShowsCards() {
        assertFalse(DeviceAnnouncePolicy.showCard(on(DeviceKind.NETWORK_WIFI), late, false))
        assertFalse(DeviceAnnouncePolicy.showCard(off(DeviceKind.NETWORK_WIFI), late, false))
    }

    @Test fun quietDuringSplash_andFirst5s() {
        assertFalse(DeviceAnnouncePolicy.showCard(on(DeviceKind.USB_STORAGE), late, splashRunning = true))
        assertFalse(DeviceAnnouncePolicy.showCard(on(DeviceKind.USB_STORAGE), 0L, false))
        assertFalse(DeviceAnnouncePolicy.showCard(on(DeviceKind.USB_STORAGE), 4_999L, false))
        assertTrue(DeviceAnnouncePolicy.showCard(on(DeviceKind.USB_STORAGE), 5_000L, false))
    }

    @Test fun builtInNames_neverShow() {
        val e = DeviceEvent.Connected(dev(DeviceKind.KEYBOARD, name = "MTK TV KEYPAD"))
        assertFalse(DeviceAnnouncePolicy.showCard(e, late, false))
    }

    @Test fun sound_connectLaunch_disconnectBack() {
        assertEquals(Sound.CONNECT, DeviceAnnouncePolicy.sound(on(DeviceKind.USB_STORAGE), newCard = true))
        assertEquals(Sound.DISCONNECT, DeviceAnnouncePolicy.sound(off(DeviceKind.USB_STORAGE), newCard = true))
        val upd = DeviceEvent.Updated(dev(DeviceKind.CONSOLE_PLAYSTATION), dev(DeviceKind.HDMI_OTHER))
        assertEquals(Sound.CONNECT, DeviceAnnouncePolicy.sound(upd, newCard = true))
        assertEquals(Sound.NONE, DeviceAnnouncePolicy.sound(on(DeviceKind.USB_STORAGE), newCard = false))
    }

    @Test fun coalescing_decidesNewCard() {
        val a = on(DeviceKind.USB_STORAGE)
        assertTrue(DeviceAnnouncePolicy.isNewCard(a, prior = null, offer = ToastQueue.Offer.QUEUED))
        assertFalse(DeviceAnnouncePolicy.isNewCard(a, prior = a, offer = ToastQueue.Offer.IGNORED))
        // Replacing a pending card for the same device in the same direction: coalesced.
        assertFalse(DeviceAnnouncePolicy.isNewCard(a, prior = a, offer = ToastQueue.Offer.QUEUED))
        val upd = DeviceEvent.Updated(dev(DeviceKind.USB_STORAGE), dev(DeviceKind.USB_STORAGE))
        assertFalse(DeviceAnnouncePolicy.isNewCard(upd, prior = a, offer = ToastQueue.Offer.PREEMPT))
        // Opposite direction pre-empts and is a new announcement.
        assertTrue(DeviceAnnouncePolicy.isNewCard(off(DeviceKind.USB_STORAGE), prior = a, offer = ToastQueue.Offer.PREEMPT))
    }

    @Test fun queue_reportsPriorEventForDevice() {
        val q = ToastQueue(3)
        val a = on(DeviceKind.USB_STORAGE)
        assertEquals(null, q.eventFor(a.device.id))
        q.offer(a)
        assertEquals(a, q.eventFor(a.device.id))
        q.advance()
        assertEquals(a, q.eventFor(a.device.id))
    }
}
