package dev.tvlauncher.network

import dev.tvlauncher.R
import org.junit.Assert.assertEquals
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
}
