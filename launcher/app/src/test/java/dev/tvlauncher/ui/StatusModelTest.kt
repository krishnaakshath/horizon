package dev.tvlauncher.ui

import dev.tvlauncher.ui.StatusModel.NetKind
import dev.tvlauncher.ui.StatusModel.NetIcon
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

    @Test fun showBluetooth_onlyWhenPresentAndEnabled() {
        assertEquals(false, StatusModel.showBluetooth(present = false, enabled = false))
        assertEquals(false, StatusModel.showBluetooth(present = true, enabled = false))
        assertEquals(true, StatusModel.showBluetooth(present = true, enabled = true))
    }
}
