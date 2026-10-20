package dev.tvlauncher.link

import dev.tvlauncher.link.core.LinkContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemoteTest {
    @Test fun address_becomesHttpUrlOnTheLinkPort() {
        assertEquals("http://192.168.1.20:8765", PhoneRemoteText.url("192.168.1.20"))
        assertEquals("http://192.168.1.20:8765", PhoneRemoteText.url("192.168.1.20:8765"))
        assertEquals("http://192.168.1.20:8765", PhoneRemoteText.url("http://192.168.1.20:8765/"))
        assertEquals("", PhoneRemoteText.url(""))
        assertEquals("http://10.0.0.2:8765/#pair=123456", PhoneRemoteText.pairUrl("10.0.0.2", "123456"))
    }

    @Test fun pairUrl_fitsASmallQrCode() {
        val q = QrCode.encode(PhoneRemoteText.pairUrl("192.168.100.200", "999999"))!!
        assertTrue(q.version <= 4)
    }

    @Test fun statusWords() {
        assertEquals("Not installed", PhoneRemoteText.status(LinkStatus(false)))
        assertEquals("Running", PhoneRemoteText.status(LinkStatus(true, enabled = true, running = true)))
        assertEquals("Stopped", PhoneRemoteText.status(LinkStatus(true)))
        assertEquals("No phones yet", PhoneRemoteText.paired(0))
        assertEquals("2 phones", PhoneRemoteText.paired(2))
        assertEquals(10, PhoneRemoteText.minutesLeft(600_000, 0))
    }

    @Test fun otherAppInFront_onlyHomeIsOurs() {
        assertEquals(LinkContract.Outcome.LAUNCHER, LinkCommands.outsideOutcome(LinkContract.Key.HOME))
        assertEquals(LinkContract.Outcome.NEEDS_HELPER, LinkCommands.outsideOutcome(LinkContract.Key.BACK))
        assertEquals(LinkContract.Outcome.NEEDS_HELPER, LinkCommands.outsideOutcome(LinkContract.Key.UP))
    }

    @Test fun launch_refusesHiddenFirmwareAndInvalidPackages() {
        assertTrue(LinkCommands.launchAllowed("com.netflix.ninja"))
        assertFalse(LinkCommands.launchAllowed("com.tcl.tvweishi"))
        assertFalse(LinkCommands.launchAllowed("com.android.tv.settings"))
        assertFalse(LinkCommands.launchAllowed("dev.tvlauncher.link"))
        assertFalse(LinkCommands.launchAllowed("not a package"))
        assertFalse(LinkCommands.launchAllowed(null))
    }
}
