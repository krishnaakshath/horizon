package dev.tvlauncher.network

import dev.tvlauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIconsTest {
    @Test fun wifi_levelMapping() {
        assertEquals(R.drawable.ic_net_wifi_0, NetIcons.forWifiLevel(0, true))
        assertEquals(R.drawable.ic_net_wifi_2, NetIcons.forWifiLevel(2, true))
        assertEquals(R.drawable.ic_net_wifi_4, NetIcons.forWifiLevel(4, true))
        assertEquals(R.drawable.ic_net_wifi_4, NetIcons.forWifiLevel(9, true))
        assertEquals(R.drawable.ic_net_wifi_0, NetIcons.forWifiLevel(-3, true))
        assertEquals(R.drawable.ic_net_wifi_off, NetIcons.forWifiLevel(3, false))
    }
    @Test fun bluetooth_mapping() {
        assertEquals(R.drawable.ic_net_bt_off, NetIcons.forBluetooth(NetIcons.BtState.OFF))
        assertEquals(R.drawable.ic_net_bt_on, NetIcons.forBluetooth(NetIcons.BtState.ON))
        assertEquals(R.drawable.ic_net_bt_connected, NetIcons.forBluetooth(NetIcons.BtState.CONNECTED))
    }

    @Test fun forWifiRssi_hardCodedEdges() {
        val expected = listOf(
            -127 to R.drawable.ic_net_wifi_0, -100 to R.drawable.ic_net_wifi_0, -89 to R.drawable.ic_net_wifi_0,
            -88 to R.drawable.ic_net_wifi_1, -78 to R.drawable.ic_net_wifi_1,
            -77 to R.drawable.ic_net_wifi_2, -67 to R.drawable.ic_net_wifi_2,
            -66 to R.drawable.ic_net_wifi_3, -56 to R.drawable.ic_net_wifi_3,
            -55 to R.drawable.ic_net_wifi_4, -30 to R.drawable.ic_net_wifi_4
        )
        for ((rssi, id) in expected) assertEquals("rssi $rssi", id, NetIcons.forWifiRssi(rssi, true))
        for ((rssi, _) in expected) assertEquals(R.drawable.ic_net_wifi_off, NetIcons.forWifiRssi(rssi, false))
    }

    @Test fun rssi_bucketEdges() {
        fun lvl(r: Int) = WifiNetworkModel.levelOf(r)
        assertEquals(0, lvl(-100)); assertEquals(0, lvl(-89))
        assertEquals(1, lvl(-88)); assertEquals(1, lvl(-78))
        assertEquals(2, lvl(-77)); assertEquals(2, lvl(-67))
        assertEquals(3, lvl(-66)); assertEquals(3, lvl(-56))
        assertEquals(4, lvl(-55))
        assertEquals(R.drawable.ic_net_wifi_4, NetIcons.forWifiRssi(-55, true))
        assertEquals(R.drawable.ic_net_wifi_off, NetIcons.forWifiRssi(-55, false))
    }

    @Test fun rssi_monotonic() {
        var prev = 0
        for (r in -127..-20) {
            val l = WifiNetworkModel.levelOf(r)
            assertTrue("rssi $r", l >= prev)
            prev = l
        }
        assertEquals(4, prev)
    }
}
