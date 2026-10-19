package dev.tvlauncher.ui

import dev.tvlauncher.R
import dev.tvlauncher.network.NetIcons
import dev.tvlauncher.network.WifiConnection
import dev.tvlauncher.network.WifiConnection.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The top-bar pills and the network panel header must go through the same icon functions. */
class StatusPillsTest {
    private fun wifi(rssi: Int) = StatusModel.status(Kind.WIFI, rssi, true, null)

    @Test fun pillIcon_equalsPanelHeaderIcon_forEveryRssi() {
        for (rssi in -127..-30) {
            val pill = StatusModel.networkPillIcon(wifi(rssi))
            val header = NetIcons.forConnection(Kind.WIFI, WifiConnection.rssiOrNull(rssi))
            assertEquals("rssi $rssi", header, pill)
            // And both equal the 0..4 mapping on the raw dBm value.
            assertEquals("rssi $rssi", NetIcons.forWifiRssi(rssi, true), pill)
        }
    }

    @Test fun statusModelLevel_isNotUsedForThePill() {
        var differing = 0
        for (rssi in -127..-30) {
            val legacy = NetIcons.forWifiLevel(StatusModel.wifiLevel(rssi), true) // 0..3, never level 4
            val pill = StatusModel.networkPillIcon(wifi(rssi))
            if (legacy != pill) differing++
        }
        assertTrue("the 0..4 scale must differ from StatusModel's 0..3 somewhere", differing > 0)
        assertEquals(R.drawable.ic_net_wifi_4, StatusModel.networkPillIcon(wifi(-55)))
        assertEquals(R.drawable.ic_net_wifi_3, StatusModel.networkPillIcon(wifi(-60)))
        val all = (-127..-30).map { StatusModel.networkPillIcon(wifi(it)) }.toSet()
        assertEquals(5, all.size)
    }

    @Test fun ethernet_and_none() {
        assertEquals(R.drawable.ic_net_ethernet, StatusModel.networkPillIcon(StatusModel.Status(Kind.ETHERNET, null, true, null)))
        assertEquals(R.drawable.ic_net_ethernet, StatusModel.networkPillIcon(StatusModel.status(Kind.ETHERNET, -40, true, null)))
        assertEquals(R.drawable.ic_net_wifi_off, StatusModel.networkPillIcon(StatusModel.Status(Kind.NONE, null, true, null)))
        assertEquals(R.drawable.ic_net_wifi_off, StatusModel.networkPillIcon(StatusModel.Status(Kind.NONE, null, false, null)))
        // Wi-Fi with unreadable signal: same as the panel header (lowest level, still connected).
        assertEquals(NetIcons.forConnection(Kind.WIFI, null), StatusModel.networkPillIcon(StatusModel.Status(Kind.WIFI, null, true, null)))
        assertEquals(R.drawable.ic_net_wifi_0, StatusModel.networkPillIcon(StatusModel.Status(Kind.WIFI, null, true, null)))
    }

    @Test fun connectionKind_matchesController() {
        assertEquals(Kind.ETHERNET, StatusModel.connectionKind(hasNetwork = true, ethernet = true, wifi = true))
        assertEquals(Kind.WIFI, StatusModel.connectionKind(hasNetwork = true, ethernet = false, wifi = true))
        assertEquals(Kind.NONE, StatusModel.connectionKind(hasNetwork = true, ethernet = false, wifi = false))
        assertEquals(Kind.NONE, StatusModel.connectionKind(hasNetwork = false, ethernet = true, wifi = true))
    }

    @Test fun rssiOrNull_rejectsImpossibleValues() {
        assertNull(WifiConnection.rssiOrNull(null))
        assertNull(WifiConnection.rssiOrNull(-127))
        assertNull(WifiConnection.rssiOrNull(1))
        assertNull(WifiConnection.rssiOrNull(Int.MIN_VALUE))
        assertEquals(-126, WifiConnection.rssiOrNull(-126))
        assertEquals(0, WifiConnection.rssiOrNull(0))
    }

    @Test fun bluetooth_stateAndIcon() {
        assertNull(StatusModel.btState(present = false, enabled = true, connected = true))
        assertEquals(NetIcons.BtState.OFF, StatusModel.btState(present = true, enabled = false, connected = true))
        assertEquals(NetIcons.BtState.ON, StatusModel.btState(present = true, enabled = true, connected = false))
        assertEquals(NetIcons.BtState.CONNECTED, StatusModel.btState(present = true, enabled = true, connected = true))
        assertNull(StatusModel.bluetoothPillIcon(StatusModel.Status(Kind.NONE, null, true, null)))
        for (s in NetIcons.BtState.values()) {
            assertEquals(NetIcons.forBluetooth(s), StatusModel.bluetoothPillIcon(StatusModel.Status(Kind.NONE, null, true, s)))
        }
    }

    @Test fun status_storesTheBucket_notTheRawRssi() {
        assertEquals(StatusModel.status(Kind.WIFI, -60, true, null), StatusModel.status(Kind.WIFI, -58, true, null))
        assertEquals(3, StatusModel.status(Kind.WIFI, -60, true, null).wifiLevel)
        assertNull(StatusModel.status(Kind.WIFI, -127, true, null).wifiLevel)
        assertNull(StatusModel.status(Kind.ETHERNET, -40, true, null).wifiLevel)
    }

    @Test fun networkLabel() {
        assertEquals("Ethernet", StatusModel.networkLabel(StatusModel.Status(Kind.ETHERNET, null, false, null)))
        assertEquals("Wi-Fi", StatusModel.networkLabel(StatusModel.Status(Kind.WIFI, -50, true, null)))
        assertEquals("Not connected", StatusModel.networkLabel(StatusModel.Status(Kind.NONE, null, true, null)))
        assertEquals("Offline", StatusModel.networkLabel(StatusModel.Status(Kind.NONE, null, false, null)))
    }
}
