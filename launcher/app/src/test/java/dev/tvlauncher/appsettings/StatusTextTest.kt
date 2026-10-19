package dev.tvlauncher.appsettings

import dev.tvlauncher.ui.StatusModel.NetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusTextTest {
    @Test
    fun ssid_stripsQuotesAndRejectsUnknown() {
        assertEquals("Home 5G", StatusText.ssid("\"Home 5G\""))
        assertNull(StatusText.ssid("<unknown ssid>"))
        assertNull(StatusText.ssid(""))
        assertNull(StatusText.ssid(null))
    }

    @Test
    fun wifi_describesEachState() {
        assertEquals("Home · Signal 3 of 4", StatusText.wifi(NetKind.WIFI, "Home", 2))
        assertEquals("Connected · Signal 4 of 4", StatusText.wifi(NetKind.WIFI, null, 3))
        assertEquals("Ethernet connected", StatusText.wifi(NetKind.ETHERNET, null, 0))
        assertEquals("Not connected", StatusText.wifi(NetKind.WIFI_DISCONNECTED, null, 0))
        assertEquals("Off", StatusText.wifi(NetKind.NONE, null, 0))
    }

    @Test
    fun bluetooth_describesEachState() {
        assertEquals("Not available", StatusText.bluetooth(present = false, enabled = false, connected = false))
        assertEquals("Off", StatusText.bluetooth(present = true, enabled = false, connected = false))
        assertEquals("On", StatusText.bluetooth(present = true, enabled = true, connected = false))
        assertEquals("On · Device connected", StatusText.bluetooth(present = true, enabled = true, connected = true))
    }
}
