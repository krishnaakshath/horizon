package dev.tvlauncher.network

import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.Trailing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetPanelModelsTest {
    private fun scan(ssid: String, bssid: String, level: Int, caps: String = "[WPA2-PSK-CCMP][ESS]", freq: Int = 5180) =
        RawScan(ssid, bssid, level, freq, caps)

    private fun wifi(
        conn: WifiConnection = WifiConnection.NONE,
        enabled: Boolean = true,
        perm: Boolean = true,
        location: Boolean = true,
        scanning: Boolean = false,
        scans: List<RawScan> = emptyList(),
        throttled: Int = 0
    ) = WifiSnapshot(conn, enabled, perm, location, scanning, scans, throttled)

    private val home = WifiConnection(WifiConnection.Kind.WIFI, "Home", "aa", -50, 400, 5180, "192.168.1.2")

    // ---- Wi-Fi

    @Test fun wifi_toggleFirst_connectedNext_settingsAlwaysLast() {
        val c = WifiPanelModel.build(wifi(conn = home, scans = listOf(
            scan("Cafe", "cc", -60, caps = "[ESS]"), scan("Home", "aa", -50), scan("Office", "oo", -40)
        )))
        val keys = c.rows.map { it.key }
        assertEquals(WifiPanelModel.KEY_TOGGLE, keys.first())
        assertEquals("net:Home", keys[1])
        assertEquals(listOf("net:Office", "net:Cafe"), keys.subList(2, 4))
        assertEquals(WifiPanelModel.KEY_SCAN, keys[4])
        assertEquals(WifiPanelModel.KEY_SETTINGS, keys.last())
        assertEquals(Trailing.Switch(true), c.rows.first().trailing)
    }

    @Test fun wifi_connectedHasCheck_securedHasLock_openHasNothing() {
        val rows = WifiPanelModel.build(wifi(conn = home, scans = listOf(
            scan("Home", "aa", -50), scan("Cafe", "cc", -60, caps = "[ESS]"), scan("Lab", "ll", -70)
        ))).rows.associateBy { it.key }
        assertEquals(Trailing.Check, rows.getValue("net:Home").trailing)
        assertEquals("Connected", rows.getValue("net:Home").value)
        assertEquals(Trailing.None, rows.getValue("net:Cafe").trailing)
        assertEquals(Trailing.Lock, rows.getValue("net:Lab").trailing)
    }

    @Test fun wifi_signalIconFollowsLevel() {
        val r = WifiPanelModel.networkRow(WifiNetworkModel("X", "b", -100, 2412, WifiSecurity.OPEN, false))
        assertEquals(R.drawable.ic_net_wifi_0, r.icon)
        val s = WifiPanelModel.networkRow(WifiNetworkModel("Y", "b", -40, 2412, WifiSecurity.WPA2, false))
        assertEquals(R.drawable.ic_net_wifi_4, s.icon)
    }

    @Test fun wifi_headerNamesConnection() {
        val h = WifiPanelModel.header(wifi(conn = home))
        assertEquals("Home", h.title)
        assertEquals("Connected · Excellent signal · 5 GHz", h.subtitle)
        val eth = WifiPanelModel.header(wifi(conn = WifiConnection(WifiConnection.Kind.ETHERNET, null, null, null, 0, 0, "10.0.0.5")))
        assertEquals("Ethernet", eth.title)
        assertEquals("Connected · 10.0.0.5", eth.subtitle)
        assertEquals("Off", WifiPanelModel.header(wifi(enabled = false)).subtitle)
        assertEquals("Not connected", WifiPanelModel.header(wifi()).subtitle)
    }

    @Test fun wifi_states_areOneFriendlyMessage() {
        fun msgKeys(s: WifiSnapshot) = WifiPanelModel.build(s).messages.map { it.key }
        assertEquals(listOf("msg:permission"), msgKeys(wifi(perm = false)))
        assertEquals(listOf("msg:off"), msgKeys(wifi(enabled = false)))
        assertEquals(listOf("msg:location"), msgKeys(wifi(location = false)))
        assertEquals(listOf("msg:scanning"), msgKeys(wifi(scanning = true)))
        assertEquals(listOf("msg:empty"), msgKeys(wifi()))
        assertEquals(emptyList<String>(), msgKeys(wifi(scans = listOf(scan("A", "a", -50)))))
        assertTrue(WifiPanelModel.build(wifi(scanning = true)).messages.single().busy)
        val perm = WifiPanelModel.build(wifi(perm = false)).messages.single()
        assertNotNull(perm.detail)
        assertTrue(!perm.detail!!.contains("adb")) // friendly: the setup pointer, never a command
    }

    @Test fun wifi_searchRowOnlyWhenItCanWork() {
        fun keys(s: WifiSnapshot) = WifiPanelModel.build(s).rows.map { it.key }
        assertFalse(WifiPanelModel.KEY_SCAN in keys(wifi(perm = false)))
        assertFalse(WifiPanelModel.KEY_SCAN in keys(wifi(enabled = false)))
        assertFalse(WifiPanelModel.KEY_SCAN in keys(wifi(scanning = true)))
        assertTrue(WifiPanelModel.KEY_LOCATION in keys(wifi(location = false)))
        val throttled = WifiPanelModel.build(wifi(scans = listOf(scan("A", "a", -50)), throttled = 40)).rows.first { it.key == WifiPanelModel.KEY_SCAN }
        assertEquals("Wait 40 s", throttled.value)
        val busy = WifiPanelModel.build(wifi(scans = listOf(scan("A", "a", -50)), scanning = true)).rows.first { it.key == WifiPanelModel.KEY_SCAN }
        assertEquals(Trailing.Busy, busy.trailing)
        assertNull(busy.value)
    }

    @Test fun wifi_networkListIsCappedButKeepsConnected() {
        val many = (1..40).map { scan("N$it", "b$it", -40 - it) } + scan("Mine", "m", -95)
        val c = WifiPanelModel.build(wifi(conn = home.copy(ssid = "Mine", bssid = "m", rssi = -95), scans = many))
        val nets = c.rows.filter { it.key.startsWith("net:") }
        assertEquals(WifiPanelModel.MAX_NETWORKS, nets.size)
        assertEquals("net:Mine", nets.first().key)
        assertEquals(WifiPanelModel.KEY_SETTINGS, c.rows.last().key)
    }

    @Test fun wifi_everyKeyIsUnique() {
        val c = WifiPanelModel.build(wifi(conn = home, scans = listOf(scan("", "h1", -50), scan("", "h2", -60), scan("Home", "aa", -50))))
        assertEquals(c.entries.size, c.entries.map { it.key }.toSet().size)
    }

    // ---- Bluetooth

    private fun bt(
        enabled: Boolean = true,
        present: Boolean = true,
        scanning: Boolean = false,
        bonded: List<BtRaw> = emptyList(),
        found: List<BtRaw> = emptyList(),
        connected: Set<String> = emptySet(),
        pairing: Map<String, PairState> = emptyMap(),
        note: BluetoothController.Note = BluetoothController.Note.NONE,
        location: Boolean = true
    ) = BtSnapshot(present, enabled, scanning, bonded, found, connected, pairing, note, location)

    @Test fun bt_order_toggle_connected_paired_nearby_search_settings() {
        val c = BtPanelModel.build(bt(
            bonded = listOf(BtRaw("01", "Pad"), BtRaw("02", "Buds")),
            found = listOf(BtRaw("03", "Speaker")),
            connected = setOf("02")
        ))
        assertEquals(listOf("act:toggle", "bt:02", "bt:01", "bt:03", "act:scan", "act:settings"), c.rows.map { it.key })
        val byKey = c.rows.associateBy { it.key }
        assertEquals(Trailing.Check, byKey.getValue("bt:02").trailing)
        assertEquals("Paired", byKey.getValue("bt:01").value)
        assertEquals("Pair", byKey.getValue("bt:03").value)
        assertEquals("Connected to Buds", c.header.subtitle)
    }

    @Test fun bt_pairingStates() {
        val s = bt(found = listOf(BtRaw("A", "One"), BtRaw("B", "Two"), BtRaw("C", "Three")),
            pairing = mapOf("A" to PairState.PAIRING, "B" to PairState.FAILED, "C" to PairState.PAIRED))
        val r = BtPanelModel.build(s).rows.associateBy { it.key }
        assertEquals(Trailing.Busy, r.getValue("bt:A").trailing)
        assertEquals("Try again", r.getValue("bt:B").value)
        assertEquals(Trailing.Check, r.getValue("bt:C").trailing)
    }

    @Test fun bt_states() {
        fun msgs(s: BtSnapshot, notice: String? = null) = BtPanelModel.build(s, notice).messages.map { it.key }
        assertEquals(listOf("msg:absent"), msgs(bt(present = false)))
        assertEquals(listOf("act:settings"), BtPanelModel.build(bt(present = false)).rows.map { it.key })
        assertEquals(listOf("msg:off"), msgs(bt(enabled = false)))
        assertEquals(listOf("msg:scanning"), msgs(bt(scanning = true)))
        assertEquals(listOf("msg:empty"), msgs(bt()))
        assertEquals(listOf("msg:permission", "msg:empty"), msgs(bt(note = BluetoothController.Note.NEEDS_LOCATION)))
        assertEquals(listOf("msg:location"), msgs(bt(location = false)))
        assertEquals(listOf("msg:notice", "msg:empty"), msgs(bt(), notice = "Nope"))
        assertFalse("act:scan" in BtPanelModel.build(bt(enabled = false)).rows.map { it.key })
        assertEquals("Off", BtPanelModel.header(bt(enabled = false)).subtitle)
    }

    @Test fun bt_headerWithSeveralConnected() {
        val s = bt(bonded = listOf(BtRaw("1", "Alpha"), BtRaw("2", "Beta"), BtRaw("3", "Gamma")), connected = setOf("1", "2", "3"))
        assertEquals("Connected to Alpha and 2 more", BtPanelModel.header(s).subtitle)
        assertEquals(R.drawable.ic_net_bt_connected, BtPanelModel.header(s).icon)
    }

    @Test fun bt_deviceView_forgetFirst_settingsLast() {
        val d = BtDeviceModel("1", "Pad", bonded = true, connected = false, kind = BtKind.GAMEPAD)
        val c = BtPanelModel.device(d)
        assertEquals(listOf(BtPanelModel.KEY_FORGET, BtPanelModel.KEY_SETTINGS), c.rows.map { it.key })
        assertEquals(R.drawable.ic_net_kind_gamepad, c.header.icon)
        assertEquals("Paired · not connected", c.header.subtitle)
    }

    @Test fun signalWords() {
        assertEquals("Excellent signal", PanelText.signalWord(4))
        assertEquals("Very weak signal", PanelText.signalWord(0))
        val rows: List<PanelEntry.Row> = WifiPanelModel.build(wifi()).rows
        assertEquals("Wi-Fi settings", rows.last().label)
    }
}
