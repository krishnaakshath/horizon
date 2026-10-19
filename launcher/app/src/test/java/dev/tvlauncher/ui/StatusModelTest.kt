package dev.tvlauncher.ui

import dev.tvlauncher.ui.StatusModel.NetKind
import dev.tvlauncher.ui.StatusModel.NetIcon
import dev.tvlauncher.ui.StatusModel.BtIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusModelTest {
    @Test fun wifiLevel_boundaries() {
        assertEquals(0, StatusModel.wifiLevel(-127))
        assertEquals(0, StatusModel.wifiLevel(-100))
        assertEquals(0, StatusModel.wifiLevel(-86))
        assertEquals(1, StatusModel.wifiLevel(-85))
        assertEquals(1, StatusModel.wifiLevel(-71))
        assertEquals(2, StatusModel.wifiLevel(-70))
        assertEquals(2, StatusModel.wifiLevel(-56))
        assertEquals(3, StatusModel.wifiLevel(-55))
        assertEquals(3, StatusModel.wifiLevel(-20))
    }

    @Test fun wifiLevel_invalidRssi_isZero() {
        assertEquals(0, StatusModel.wifiLevel(Int.MIN_VALUE))
    }

    @Test fun iconFor_wifiLevels() {
        assertEquals(NetIcon.WIFI_0, StatusModel.iconFor(NetKind.WIFI, 0))
        assertEquals(NetIcon.WIFI_1, StatusModel.iconFor(NetKind.WIFI, 1))
        assertEquals(NetIcon.WIFI_2, StatusModel.iconFor(NetKind.WIFI, 2))
        assertEquals(NetIcon.WIFI_3, StatusModel.iconFor(NetKind.WIFI, 3))
        assertEquals(NetIcon.WIFI_3, StatusModel.iconFor(NetKind.WIFI, 9))
        assertEquals(NetIcon.WIFI_0, StatusModel.iconFor(NetKind.WIFI, -1))
    }

    @Test fun iconFor_otherKinds() {
        assertEquals(NetIcon.ETHERNET, StatusModel.iconFor(NetKind.ETHERNET, 0))
        assertEquals(NetIcon.ETHERNET, StatusModel.iconFor(NetKind.OTHER, 3))
        assertEquals(NetIcon.WIFI_OFF, StatusModel.iconFor(NetKind.WIFI_DISCONNECTED, 3))
        assertEquals(NetIcon.OFFLINE, StatusModel.iconFor(NetKind.NONE, 3))
    }

    @Test fun netKind_ethernetPreferredOverWifi() {
        assertEquals(NetKind.ETHERNET, StatusModel.netKind(hasNetwork = true, ethernet = true, wifi = true, wifiEnabled = true))
        assertEquals(NetKind.WIFI, StatusModel.netKind(hasNetwork = true, ethernet = false, wifi = true, wifiEnabled = true))
        assertEquals(NetKind.OTHER, StatusModel.netKind(hasNetwork = true, ethernet = false, wifi = false, wifiEnabled = false))
    }

    @Test fun netKind_noNetwork_dependsOnWifiRadio() {
        assertEquals(NetKind.WIFI_DISCONNECTED, StatusModel.netKind(hasNetwork = false, ethernet = false, wifi = false, wifiEnabled = true))
        assertEquals(NetKind.NONE, StatusModel.netKind(hasNetwork = false, ethernet = false, wifi = false, wifiEnabled = false))
        // Stale transport flags without a network never count as connected.
        assertEquals(NetKind.NONE, StatusModel.netKind(hasNetwork = false, ethernet = true, wifi = true, wifiEnabled = false))
    }

    @Test fun ethernet_showsEthernetIcon_evenWithStrongWifi() {
        val kind = StatusModel.netKind(hasNetwork = true, ethernet = true, wifi = true, wifiEnabled = true)
        assertEquals(NetIcon.ETHERNET, StatusModel.iconFor(kind, 3))
    }

    @Test fun bluetoothIcon_mapping() {
        assertEquals(BtIcon.HIDDEN, StatusModel.bluetoothIcon(present = false, enabled = true, connected = true))
        assertEquals(BtIcon.HIDDEN, StatusModel.bluetoothIcon(present = true, enabled = false, connected = false))
        assertEquals(BtIcon.HIDDEN, StatusModel.bluetoothIcon(present = true, enabled = false, connected = true))
        assertEquals(BtIcon.ON, StatusModel.bluetoothIcon(present = true, enabled = true, connected = false))
        assertEquals(BtIcon.CONNECTED, StatusModel.bluetoothIcon(present = true, enabled = true, connected = true))
    }
}
