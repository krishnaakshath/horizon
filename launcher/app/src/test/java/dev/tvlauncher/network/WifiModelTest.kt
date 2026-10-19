package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiModelTest {
    private fun raw(ssid: String, bssid: String, level: Int, freq: Int = 2412, caps: String = "[WPA2-PSK-CCMP][ESS]") =
        RawScan(ssid, bssid, level, freq, caps)

    @Test fun dedupe_keepsStrongest() {
        val out = WifiListBuilder.build(
            listOf(raw("Home", "aa", -70), raw("Home", "bb", -50), raw("Cafe", "cc", -80)), null, null
        )
        assertEquals(listOf("Home", "Cafe"), out.map { it.ssid })
        assertEquals("bb", out[0].bssid)
    }

    @Test fun dedupe_prefersConnectedBssid() {
        val out = WifiListBuilder.build(
            listOf(raw("Home", "aa", -70), raw("Home", "bb", -50)), "aa", "Home"
        )
        assertEquals(1, out.size)
        assertEquals("aa", out[0].bssid)
        assertTrue(out[0].connected)
    }

    @Test fun connectedFirst() {
        val out = WifiListBuilder.build(
            listOf(raw("Strong", "s", -40), raw("Mine", "m", -85), raw("Mid", "x", -60)), "m", "Mine"
        )
        assertEquals(listOf("Mine", "Strong", "Mid"), out.map { it.ssid })
        assertTrue(out[0].connected)
        assertFalse(out[1].connected)
    }

    @Test fun connectedMatchesBySsidWhenBssidUnknown() {
        val out = WifiListBuilder.build(listOf(raw("A", "1", -40), raw("B", "2", -80)), null, "B")
        assertEquals("B", out[0].ssid)
    }

    @Test fun hidden_collapsed_toOne() {
        val out = WifiListBuilder.build(
            listOf(raw("", "h1", -80), raw("", "h2", -60), raw("<unknown ssid>", "h3", -90), raw("Net", "n", -70)),
            null, null
        )
        assertEquals(2, out.size)
        assertEquals(1, out.count { it.hidden })
        val hidden = out.first { it.hidden }
        assertEquals("Hidden network", hidden.ssid)
        assertEquals("h2", hidden.bssid)
    }

    @Test fun band_fromFrequency() {
        assertEquals(WifiBand.GHZ_2_4, WifiNetworkModel.bandOf(2412))
        assertEquals(WifiBand.GHZ_2_4, WifiNetworkModel.bandOf(2484))
        assertEquals(WifiBand.GHZ_5, WifiNetworkModel.bandOf(5180))
        assertEquals(WifiBand.GHZ_5, WifiNetworkModel.bandOf(5825))
        assertEquals(WifiBand.GHZ_6, WifiNetworkModel.bandOf(5955))
        assertEquals(WifiBand.GHZ_6, WifiNetworkModel.bandOf(7115))
        assertEquals(WifiBand.UNKNOWN, WifiNetworkModel.bandOf(0))
        assertEquals("5 GHz", WifiBand.GHZ_5.label)
    }

    @Test fun security_parse() {
        fun p(c: String) = WifiNetworkModel.securityOf(c)
        assertEquals(WifiSecurity.WPA2, p("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA3, p("[WPA3-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA2_WPA3, p("[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.OPEN, p("[ESS]"))
        assertEquals(WifiSecurity.WEP, p("[WEP][ESS]"))
        assertEquals(WifiSecurity.ENTERPRISE, p("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA, p("[WPA-PSK-TKIP][ESS]"))
        assertEquals(WifiSecurity.WPA2, p("[WPA-PSK-TKIP][WPA2-PSK-CCMP][ESS]"))
        assertTrue(WifiSecurity.OPEN.isOpen)
        assertFalse(WifiSecurity.WPA2.isOpen)
        assertEquals("WPA2/WPA3", WifiSecurity.WPA2_WPA3.label)
    }

    @Test fun level_boundaries() {
        assertEquals(0, WifiNetworkModel.levelOf(-100))
        assertEquals(0, WifiNetworkModel.levelOf(-127))
        assertEquals(0, WifiNetworkModel.levelOf(-89))
        assertEquals(1, WifiNetworkModel.levelOf(-88))
        assertEquals(2, WifiNetworkModel.levelOf(-70))
        assertEquals(3, WifiNetworkModel.levelOf(-60))
        assertEquals(4, WifiNetworkModel.levelOf(-55))
        assertEquals(4, WifiNetworkModel.levelOf(-20))
        assertEquals(4, WifiNetworkModel.MAX_LEVEL)
    }
}
