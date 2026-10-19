package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkPanelStateTest {
    @Test fun wifi_permissionMissing_wins() {
        assertEquals(WifiContent.PERMISSION_MISSING, NetworkPanelState.wifi(false, true, true, 5))
    }
    @Test fun wifi_off() {
        assertEquals(WifiContent.WIFI_OFF, NetworkPanelState.wifi(true, false, false, 0))
    }
    @Test fun wifi_listShownEvenWhileScanning() {
        assertEquals(WifiContent.LIST, NetworkPanelState.wifi(true, true, true, 3))
    }
    @Test fun wifi_scanningThenEmpty() {
        assertEquals(WifiContent.SCANNING, NetworkPanelState.wifi(true, true, true, 0))
        assertEquals(WifiContent.EMPTY, NetworkPanelState.wifi(true, true, false, 0))
    }

    @Test fun bt_states() {
        assertEquals(BtContent.NO_ADAPTER, NetworkPanelState.bluetooth(false, false, false, 0))
        assertEquals(BtContent.OFF, NetworkPanelState.bluetooth(true, false, false, 0))
        assertEquals(BtContent.LIST, NetworkPanelState.bluetooth(true, true, true, 2))
        assertEquals(BtContent.SCANNING, NetworkPanelState.bluetooth(true, true, true, 0))
        assertEquals(BtContent.EMPTY, NetworkPanelState.bluetooth(true, true, false, 0))
    }

    @Test fun pair_reducer() {
        val none = 10; val bonding = 11; val bonded = 12
        assertEquals(PairState.PAIRING, NetworkPanelState.pair(PairState.IDLE, bonding))
        assertEquals(PairState.PAIRED, NetworkPanelState.pair(PairState.PAIRING, bonded))
        assertEquals(PairState.FAILED, NetworkPanelState.pair(PairState.PAIRING, none))
        assertEquals(PairState.IDLE, NetworkPanelState.pair(PairState.IDLE, none))
        assertEquals(PairState.PAIRED, NetworkPanelState.pair(PairState.IDLE, bonded))
    }

    @Test fun scanThrottle_fourPerTwoMinutes() {
        val t = ScanThrottle()
        for (i in 0 until 4) { assertTrue(t.tryAcquire(i * 1000L)); }
        assertFalse(t.tryAcquire(5_000))
        assertEquals(115, t.retryAfterSeconds(5_000))
        assertTrue(t.tryAcquire(120_001))
    }
}
