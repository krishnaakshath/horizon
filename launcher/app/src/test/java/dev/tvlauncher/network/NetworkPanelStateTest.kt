package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkPanelStateTest {
    @Test fun permissionMissing_showsPermissionState_first() {
        assertEquals(WifiContent.PERMISSION_MISSING, NetworkPanelState.wifi(false, false, true, true, 5))
        assertEquals(WifiContent.PERMISSION_MISSING, NetworkPanelState.wifi(false, true, true, true, 5))
    }
    @Test fun permissionGranted_locationOff_showsLocationOffState() {
        assertEquals(WifiContent.LOCATION_OFF, NetworkPanelState.wifi(true, false, true, false, 0))
        assertEquals(WifiContent.LOCATION_OFF, NetworkPanelState.wifi(true, false, true, true, 0))
    }
    @Test fun bothOk_showsList() {
        assertEquals(WifiContent.LIST, NetworkPanelState.wifi(true, true, true, true, 3))
    }
    @Test fun emptyList_withBothOk_showsEmptyNotLocation() {
        assertEquals(WifiContent.EMPTY, NetworkPanelState.wifi(true, true, true, false, 0))
        assertEquals(WifiContent.SCANNING, NetworkPanelState.wifi(true, true, true, true, 0))
    }
    @Test fun wifi_off() {
        assertEquals(WifiContent.WIFI_OFF, NetworkPanelState.wifi(true, true, false, false, 0))
    }

    @Test fun bt_states() {
        assertEquals(BtContent.NO_ADAPTER, NetworkPanelState.bluetooth(false, false, true, false, 0))
        assertEquals(BtContent.OFF, NetworkPanelState.bluetooth(true, false, true, false, 0))
        assertEquals(BtContent.LIST, NetworkPanelState.bluetooth(true, true, true, true, 2))
        assertEquals(BtContent.LIST, NetworkPanelState.bluetooth(true, true, false, false, 2))
        assertEquals(BtContent.SCANNING, NetworkPanelState.bluetooth(true, true, true, true, 0))
        assertEquals(BtContent.EMPTY, NetworkPanelState.bluetooth(true, true, true, false, 0))
        assertEquals(BtContent.LOCATION_OFF, NetworkPanelState.bluetooth(true, true, false, true, 0))
    }

    @Test fun pair_reducer() {
        val none = 10; val bonding = 11; val bonded = 12
        assertEquals(PairState.PAIRING, NetworkPanelState.pair(PairState.IDLE, bonding))
        assertEquals(PairState.PAIRED, NetworkPanelState.pair(PairState.PAIRING, bonded))
        assertEquals(PairState.FAILED, NetworkPanelState.pair(PairState.PAIRING, none))
        assertEquals(PairState.IDLE, NetworkPanelState.pair(PairState.IDLE, none))
        assertEquals(PairState.PAIRED, NetworkPanelState.pair(PairState.IDLE, bonded))
    }

    @Test fun pair_failedCanRetry() {
        assertEquals(PairState.PAIRING, NetworkPanelState.pair(PairState.FAILED, 11))
    }

    @Test fun scanThrottle_freshness() {
        val t = ScanThrottle()
        assertFalse(t.isFresh(1_000, 30_000))
        t.tryAcquire(1_000)
        assertTrue(t.isFresh(20_000, 30_000))
        assertFalse(t.isFresh(31_001, 30_000))
    }

    @Test fun scanThrottle_fourPerTwoMinutes() {
        val t = ScanThrottle()
        for (i in 0 until 4) { assertTrue(t.tryAcquire(i * 1000L)); }
        assertFalse(t.tryAcquire(5_000))
        assertEquals(115, t.retryAfterSeconds(5_000))
        assertTrue(t.tryAcquire(120_001))
    }
}
