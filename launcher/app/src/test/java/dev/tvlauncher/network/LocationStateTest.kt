package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStateTest {
    @Test fun bt_api27_toggleNotRequired() = assertFalse(LocationState.requiredForBluetoothDiscovery(27))
    @Test fun bt_api28_toggleRequired() = assertTrue(LocationState.requiredForBluetoothDiscovery(28))
    @Test fun bt_api30_toggleRequired() = assertTrue(LocationState.requiredForBluetoothDiscovery(30))

    @Test fun btLocationOk_gatedBySdk() {
        assertTrue(LocationState.bluetoothLocationOk(27, toggleOn = false))
        assertFalse(LocationState.bluetoothLocationOk(28, toggleOn = false))
        assertTrue(LocationState.bluetoothLocationOk(28, toggleOn = true))
    }

    @Test fun btApi27_locationOff_showsScanStateNotLocationOff() {
        val ok = LocationState.bluetoothLocationOk(27, toggleOn = false)
        assertEquals(BtContent.SCANNING, NetworkPanelState.bluetooth(true, true, ok, true, 0))
        assertEquals(BtContent.EMPTY, NetworkPanelState.bluetooth(true, true, ok, false, 0))
        assertEquals(BtContent.LIST, NetworkPanelState.bluetooth(true, true, ok, false, 2))
    }

    @Test fun btApi30_locationOff_showsLocationOff() {
        val ok = LocationState.bluetoothLocationOk(30, toggleOn = false)
        assertEquals(BtContent.LOCATION_OFF, NetworkPanelState.bluetooth(true, true, ok, false, 0))
    }
}
