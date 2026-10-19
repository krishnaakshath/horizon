package dev.tvlauncher.network

import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.Trailing

/**
 * Pure content of the Wi-Fi panel: the header names the current connection and its signal; rows
 * are the Wi-Fi switch, the connected network, nearby networks (signal icon, lock when secured),
 * "Search again" and, always last, the TV's Wi-Fi settings (apps targeting API 29+ cannot join or
 * toggle Wi-Fi, so connecting happens there). Empty, loading and permission states are one-line
 * messages.
 */
object WifiPanelModel {
    const val KEY_TOGGLE = "act:toggle"
    const val KEY_SCAN = "act:scan"
    const val KEY_SETTINGS = "act:settings"
    const val KEY_LOCATION = "act:location"
    /** Nearby networks listed at most (the strongest; the connected one always stays). */
    const val MAX_NETWORKS = 15

    fun netKey(m: WifiNetworkModel) = "net:" + if (m.hidden) "hidden" else m.ssid

    fun header(s: WifiSnapshot): PanelHeader {
        val c = s.connection
        val icon = NetIcons.forConnection(c.kind, c.rssi)
        return when (c.kind) {
            WifiConnection.Kind.ETHERNET -> PanelHeader(icon, "Ethernet", listOfNotNull("Connected", c.ip).joinToString(" · "))
            WifiConnection.Kind.WIFI -> PanelHeader(icon, c.ssid ?: "Wi-Fi", listOfNotNull(
                "Connected",
                if (c.rssi != null) PanelText.signalWord(c.level) else null,
                c.band.label.ifEmpty { null }
            ).joinToString(" · "))
            WifiConnection.Kind.NONE -> PanelHeader(icon, "Wi-Fi", if (s.wifiEnabled) "Not connected" else "Off")
        }
    }

    fun build(s: WifiSnapshot): PanelContent {
        val models = WifiListBuilder.build(s.scans, s.connection.bssid, s.connection.ssid)
        val content = NetworkPanelState.wifi(s.hasLocationPermission, s.locationEnabled, s.wifiEnabled, s.scanning, models.size)
        val e = ArrayList<PanelEntry>()
        e += PanelEntry.Row(KEY_TOGGLE, NetIcons.forWifiLevel(4, s.wifiEnabled), "Wi-Fi", trailing = Trailing.Switch(s.wifiEnabled))
        when (content) {
            WifiContent.PERMISSION_MISSING -> e += PanelEntry.Message("msg:permission",
                "Allow location access to see nearby networks.", "From a computer: ${NetworkSettings.ADB_LOCATION_GRANT}")
            WifiContent.LOCATION_OFF -> {
                e += PanelEntry.Message("msg:location", "Turn on Location to see nearby networks.")
                e += PanelEntry.Row(KEY_LOCATION, R.drawable.ic_settings, "Location settings", trailing = Trailing.Chevron)
            }
            WifiContent.WIFI_OFF -> e += PanelEntry.Message("msg:off", "Wi-Fi is off. Turn it on in Wi-Fi settings.")
            WifiContent.SCANNING -> e += PanelEntry.Message("msg:scanning", "Looking for networks…", busy = true)
            WifiContent.EMPTY -> e += PanelEntry.Message("msg:empty", "No networks found nearby.")
            WifiContent.LIST -> networks(models).mapTo(e) { networkRow(it) }
        }
        if (s.hasLocationPermission && s.wifiEnabled && content != WifiContent.SCANNING && content != WifiContent.LOCATION_OFF) {
            e += PanelEntry.Row(KEY_SCAN, R.drawable.ic_net_refresh, "Search again",
                value = if (!s.scanning && s.throttledSeconds > 0) "Wait ${s.throttledSeconds} s" else null,
                trailing = if (s.scanning) Trailing.Busy else Trailing.None)
        }
        e += PanelEntry.Row(KEY_SETTINGS, R.drawable.ic_settings, "Wi-Fi settings", value = "Connect", trailing = Trailing.Chevron)
        return PanelContent(header(s), e)
    }

    /** Connected first (already sorted so by [WifiListBuilder]), then the strongest, capped. */
    fun networks(models: List<WifiNetworkModel>): List<WifiNetworkModel> {
        val (conn, rest) = models.partition { it.connected }
        return conn + rest.take((MAX_NETWORKS - conn.size).coerceAtLeast(0))
    }

    fun networkRow(m: WifiNetworkModel) = PanelEntry.Row(
        netKey(m),
        NetIcons.forWifiLevel(m.signalLevel, true),
        m.ssid,
        value = if (m.connected) "Connected" else null,
        trailing = when {
            m.connected -> Trailing.Check
            !m.security.isOpen -> Trailing.Lock
            else -> Trailing.None
        }
    )
}

/**
 * Pure content of the Bluetooth panel: the header names the connected device; rows are the
 * Bluetooth switch, connected devices, paired devices, nearby devices (OK pairs), "Search for
 * devices" and, always last, the TV's Bluetooth settings. A paired device opens [device], a small
 * sub-view with Forget.
 */
object BtPanelModel {
    const val KEY_TOGGLE = "act:toggle"
    const val KEY_SCAN = "act:scan"
    const val KEY_SETTINGS = "act:settings"
    const val KEY_LOCATION = "act:location"
    const val KEY_FORGET = "act:forget"

    fun devKey(d: BtDeviceModel) = "bt:" + d.address

    fun header(s: BtSnapshot, sec: BtSections = s.sections): PanelHeader {
        val state = when {
            !s.enabled -> NetIcons.BtState.OFF
            sec.connected.isNotEmpty() -> NetIcons.BtState.CONNECTED
            else -> NetIcons.BtState.ON
        }
        val sub = when {
            !s.present -> "Not available on this TV"
            !s.enabled -> "Off"
            sec.connected.size == 1 -> "Connected to ${sec.connected[0].name}"
            sec.connected.size > 1 -> "Connected to ${sec.connected[0].name} and ${sec.connected.size - 1} more"
            else -> "On · nothing connected"
        }
        return PanelHeader(NetIcons.forBluetooth(state), "Bluetooth", sub)
    }

    /** [notice] is a transient one-line message from the last action (pair refused, ...). */
    fun build(s: BtSnapshot, notice: String? = null): PanelContent {
        val sec = s.sections
        val e = ArrayList<PanelEntry>()
        if (!s.present) {
            e += PanelEntry.Message("msg:absent", "This TV has no Bluetooth.")
            e += settingsRow()
            return PanelContent(header(s, sec), e)
        }
        e += PanelEntry.Row(KEY_TOGGLE, NetIcons.forBluetooth(if (s.enabled) NetIcons.BtState.ON else NetIcons.BtState.OFF),
            "Bluetooth", trailing = Trailing.Switch(s.enabled))
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        val content = NetworkPanelState.bluetooth(s.present, s.enabled, s.locationEnabled, s.scanning, sec.all.size)
        if (s.enabled) {
            when {
                s.note == BluetoothController.Note.NEEDS_LOCATION -> e += PanelEntry.Message("msg:permission",
                    "Allow location access to find new devices.", "From a computer: ${NetworkSettings.ADB_LOCATION_GRANT}")
                !s.locationEnabled -> {
                    e += PanelEntry.Message("msg:location", "Turn on Location to find new devices.")
                    e += PanelEntry.Row(KEY_LOCATION, R.drawable.ic_settings, "Location settings", trailing = Trailing.Chevron)
                }
                s.note == BluetoothController.Note.SCAN_FAILED -> e += PanelEntry.Message("msg:scanfail",
                    "Searching didn't start here. Pair in Bluetooth settings.")
            }
        }
        when (content) {
            BtContent.OFF -> e += PanelEntry.Message("msg:off", "Turn on Bluetooth to use your devices.")
            BtContent.SCANNING -> e += PanelEntry.Message("msg:scanning", "Looking for devices…", busy = true)
            BtContent.EMPTY -> e += PanelEntry.Message("msg:empty", "No devices yet. Put one in pairing mode.")
            BtContent.LIST -> {
                sec.connected.mapTo(e) { deviceRow(it, s) }
                sec.paired.mapTo(e) { deviceRow(it, s) }
                sec.nearby.mapTo(e) { deviceRow(it, s) }
            }
            BtContent.LOCATION_OFF, BtContent.NO_ADAPTER -> Unit
        }
        if (s.enabled) {
            e += PanelEntry.Row(KEY_SCAN, R.drawable.ic_net_refresh,
                if (s.scanning) "Stop searching" else "Search for devices",
                trailing = if (s.scanning) Trailing.Busy else Trailing.None)
        }
        e += settingsRow()
        return PanelContent(header(s, sec), e)
    }

    fun deviceRow(d: BtDeviceModel, s: BtSnapshot): PanelEntry.Row {
        val pair = s.pairing[d.address] ?: PairState.IDLE
        return when {
            d.connected -> PanelEntry.Row(devKey(d), kindIcon(d.kind), d.name, "Connected", Trailing.Check)
            d.bonded -> PanelEntry.Row(devKey(d), kindIcon(d.kind), d.name, "Paired", Trailing.Chevron)
            pair == PairState.PAIRING -> PanelEntry.Row(devKey(d), kindIcon(d.kind), d.name, "Pairing", Trailing.Busy)
            pair == PairState.PAIRED -> PanelEntry.Row(devKey(d), kindIcon(d.kind), d.name, "Paired", Trailing.Check)
            pair == PairState.FAILED -> PanelEntry.Row(devKey(d), kindIcon(d.kind), d.name, "Try again")
            else -> PanelEntry.Row(devKey(d), kindIcon(d.kind), d.name, "Pair")
        }
    }

    /** Sub-view for a paired device: Forget, then the TV's settings. */
    fun device(d: BtDeviceModel): PanelContent = PanelContent(
        PanelHeader(kindIcon(d.kind), d.name, if (d.connected) "Connected" else "Paired · not connected"),
        listOf(
            PanelEntry.Row(KEY_FORGET, R.drawable.ic_net_close, "Forget this device"),
            PanelEntry.Message("msg:reconnect", "It reconnects by itself when switched on."),
            settingsRow()
        )
    )

    private fun settingsRow() =
        PanelEntry.Row(KEY_SETTINGS, R.drawable.ic_settings, "Bluetooth settings", value = "Pair or connect", trailing = Trailing.Chevron)

    fun kindIcon(k: BtKind) = when (k) {
        BtKind.HEADPHONES -> R.drawable.ic_net_kind_headphones
        BtKind.SPEAKER -> R.drawable.ic_net_kind_speaker
        BtKind.GAMEPAD -> R.drawable.ic_net_kind_gamepad
        BtKind.KEYBOARD -> R.drawable.ic_net_kind_keyboard
        BtKind.MOUSE -> R.drawable.ic_net_kind_mouse
        BtKind.PHONE -> R.drawable.ic_net_kind_phone
        BtKind.COMPUTER -> R.drawable.ic_net_kind_computer
        BtKind.WATCH -> R.drawable.ic_net_kind_watch
        BtKind.OTHER -> R.drawable.ic_net_kind_other
    }
}

/** Shared words. */
object PanelText {
    fun signalWord(level: Int) = when (level) {
        4 -> "Excellent signal"
        3 -> "Good signal"
        2 -> "Fair signal"
        1 -> "Weak signal"
        else -> "Very weak signal"
    }
}
