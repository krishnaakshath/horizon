package dev.tvlauncher.network

import dev.tvlauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetIconsTest {
    @Test fun wifi_levelMapping() {
        assertEquals(R.drawable.ic_net_wifi_0, NetIcons.forWifi(0, true))
        assertEquals(R.drawable.ic_net_wifi_2, NetIcons.forWifi(2, true))
        assertEquals(R.drawable.ic_net_wifi_4, NetIcons.forWifi(4, true))
        assertEquals(R.drawable.ic_net_wifi_4, NetIcons.forWifi(9, true))
        assertEquals(R.drawable.ic_net_wifi_0, NetIcons.forWifi(-3, true))
        assertEquals(R.drawable.ic_net_wifi_off, NetIcons.forWifi(3, false))
    }
    @Test fun bluetooth_mapping() {
        assertEquals(R.drawable.ic_net_bt_off, NetIcons.forBluetooth(NetIcons.BtState.OFF))
        assertEquals(R.drawable.ic_net_bt_on, NetIcons.forBluetooth(NetIcons.BtState.ON))
        assertEquals(R.drawable.ic_net_bt_connected, NetIcons.forBluetooth(NetIcons.BtState.CONNECTED))
    }

    private fun wifiConn(rssi: Int) = WifiConnection(WifiConnection.Kind.WIFI, "x", "b", rssi, 100, 5180, "1.2.3.4")

    @Test fun pillAndPanel_agree_forEveryRssiFrom_minus100_to_minus30() {
        for (r in -127..-30) {
            val panelHeader = NetIcons.forWifiLevel(wifiConn(r).level, true)
            assertEquals("rssi $r", panelHeader, NetIcons.forWifiRssi(r, true))
        }
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

    @Test fun statusModelAdapter_preservesOrderAndRange() {
        val m = (0..3).map { NetIcons.levelFromStatusModel(it) }
        assertEquals(listOf(0, 2, 3, 4), m)
        assertEquals(0, NetIcons.levelFromStatusModel(-5))
        assertEquals(4, NetIcons.levelFromStatusModel(9))
    }
}
