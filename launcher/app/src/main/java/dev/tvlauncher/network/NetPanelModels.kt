package dev.tvlauncher.network

import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.Trailing

/**
 * Pure content of the Wi-Fi panel: the header names the current connection and its signal; rows
 * are the Wi-Fi switch, the connected network, saved networks, nearby networks (signal icon, lock
 * when secured), "Search again" and "Add hidden network". OK on a network opens [detail]
 * (details, Connect, Reconnect, Forget). Everything happens in the launcher (legacy WifiManager,
 * targetSdk 28); the only screen left to the TV is the device-wide Location switch.
 */
object WifiPanelModel {
    const val KEY_TOGGLE = "act:toggle"
    const val KEY_SCAN = "act:scan"
    const val KEY_LOCATION = "act:location"
    const val KEY_PERMISSION = "act:permission"
    const val KEY_HIDDEN = "act:hidden"
    const val KEY_CONNECT = "act:connect"
    const val KEY_RECONNECT = "act:reconnect"
    const val KEY_FORGET = "act:forget"
    const val KEY_ETHERNET = "info:ethernet"
    const val INFO = "info:"
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

    fun models(s: WifiSnapshot): List<WifiNetworkModel> =
        WifiListBuilder.build(s.scans, s.connection.bssid, s.connection.ssid, s.saved.keys)

    /** [notice] is one line from the last action ("Wrong password", ...); [joining] the SSID being joined. */
    fun build(s: WifiSnapshot, notice: String? = null, joining: String? = null): PanelContent {
        val models = models(s)
        val content = NetworkPanelState.wifi(s.hasLocationPermission, s.locationEnabled, s.wifiEnabled, s.scanning, models.size)
        val e = ArrayList<PanelEntry>()
        e += PanelEntry.Row(KEY_TOGGLE, NetIcons.forWifiLevel(4, s.wifiEnabled), "Wi-Fi", trailing = Trailing.Switch(s.wifiEnabled))
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        if (s.connection.kind == WifiConnection.Kind.ETHERNET) {
            e += PanelEntry.Row(KEY_ETHERNET, R.drawable.ic_net_ethernet, "Ethernet", value = s.connection.ip ?: "Connected")
        }
        when (content) {
            WifiContent.PERMISSION_MISSING -> {
                e += PanelEntry.Message("msg:permission", "Allow location access to see nearby networks.")
                e += PanelEntry.Row(KEY_PERMISSION, R.drawable.ic_set_check, "Allow location access", trailing = Trailing.Chevron)
            }
            WifiContent.LOCATION_OFF -> {
                e += PanelEntry.Message("msg:location", "Location is off, so the TV can't list networks.")
                e += PanelEntry.Row(KEY_LOCATION, R.drawable.ic_settings, "Location", value = "TV settings", trailing = Trailing.Chevron)
            }
            WifiContent.WIFI_OFF -> e += PanelEntry.Message("msg:off", "Wi-Fi is off.")
            WifiContent.SCANNING -> e += PanelEntry.Message("msg:scanning", "Looking for networks…", busy = true)
            WifiContent.EMPTY -> e += PanelEntry.Message("msg:empty", "No networks found nearby.")
            WifiContent.LIST -> networks(models).mapTo(e) { networkRow(it, joining) }
        }
        if (s.hasLocationPermission && s.wifiEnabled && content != WifiContent.SCANNING && content != WifiContent.LOCATION_OFF) {
            e += PanelEntry.Row(KEY_SCAN, R.drawable.ic_net_refresh, "Search again",
                value = if (!s.scanning && s.throttledSeconds > 0) "Wait ${s.throttledSeconds} s" else null,
                trailing = if (s.scanning) Trailing.Busy else Trailing.None)
        }
        if (s.wifiEnabled) e += PanelEntry.Row(KEY_HIDDEN, R.drawable.ic_net_lock, "Add hidden network", trailing = Trailing.Chevron)
        return PanelContent(header(s), e)
    }

    /** Connected first (already sorted so by [WifiListBuilder]), then the strongest, capped. */
    fun networks(models: List<WifiNetworkModel>): List<WifiNetworkModel> {
        val (conn, rest) = models.partition { it.connected }
        return conn + rest.take((MAX_NETWORKS - conn.size).coerceAtLeast(0))
    }

    fun networkRow(m: WifiNetworkModel, joining: String? = null) = PanelEntry.Row(
        netKey(m),
        NetIcons.forWifiLevel(m.signalLevel, true),
        m.ssid,
        value = when {
            m.connected -> "Connected"
            !m.hidden && m.ssid == joining -> "Connecting"
            m.saved -> "Saved"
            else -> null
        },
        trailing = when {
            m.connected -> Trailing.Check
            !m.hidden && m.ssid == joining -> Trailing.Busy
            !m.security.isOpen -> Trailing.Lock
            else -> Trailing.None
        }
    )

    /**
     * The sub-view for one network. Connected: signal, speed, band and IP address, then Reconnect
     * and Forget. Otherwise: Connect (asks for a password when needed and not saved) and Forget
     * when saved.
     */
    fun detail(m: WifiNetworkModel, conn: WifiConnection, saved: Boolean, notice: String? = null, joining: Boolean = false): PanelContent {
        val e = ArrayList<PanelEntry>()
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        val connected = m.connected
        val sub: String
        if (connected) {
            val signal = PanelText.signalWord(conn.rssi?.let { WifiNetworkModel.levelOf(it) } ?: m.signalLevel)
            val band = (conn.band.takeIf { it != WifiBand.UNKNOWN } ?: m.band).label
            sub = listOfNotNull("Connected", band.ifEmpty { null }).joinToString(" · ")
            e += info("signal", R.drawable.ic_net_wifi_4, "Signal", signal)
            e += info("speed", R.drawable.ic_net_refresh, "Speed", if (conn.linkMbps > 0) "${conn.linkMbps} Mbps" else "Unknown")
            e += info("band", R.drawable.ic_sc_wifi, "Band", band.ifEmpty { "Unknown" })
            e += info("ip", R.drawable.ic_set_network, "IP address", conn.ip ?: "Not assigned yet")
            e += PanelEntry.Row(KEY_RECONNECT, R.drawable.ic_net_refresh, "Reconnect")
        } else {
            sub = listOf(m.security.label, PanelText.signalWord(m.signalLevel)).joinToString(" · ")
            val needsPw = !saved && !m.security.isOpen
            e += PanelEntry.Row(KEY_CONNECT, R.drawable.ic_set_check, if (joining) "Connecting…" else "Connect",
                value = if (needsPw && !joining) "Password" else null,
                trailing = if (joining) Trailing.Busy else Trailing.Chevron,
                enabled = !joining)
        }
        if (saved) e += PanelEntry.Row(KEY_FORGET, R.drawable.ic_net_close, "Forget this network")
        return PanelContent(PanelHeader(NetIcons.forWifiLevel(m.signalLevel, true), m.ssid, sub), e)
    }

    private fun info(k: String, icon: Int, label: String, value: String) = PanelEntry.Row(INFO + k, icon, label, value)
}

/**
 * Pure content of the Bluetooth panel: the header names the connected device; rows are the
 * Bluetooth switch, connected devices, paired devices, nearby devices (OK pairs) and "Search for
 * devices". A paired device opens [device]: Connect or Disconnect (where the TV allows it) and
 * Forget. Nothing opens the TV's Bluetooth screen.
 */
object BtPanelModel {
    const val KEY_TOGGLE = "act:toggle"
    const val KEY_SCAN = "act:scan"
    const val KEY_LOCATION = "act:location"
    const val KEY_PERMISSION = "act:permission"
    const val KEY_FORGET = "act:forget"
    const val KEY_CONNECT = "act:connect"
    const val KEY_DISCONNECT = "act:disconnect"

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
            return PanelContent(header(s, sec), e)
        }
        e += PanelEntry.Row(KEY_TOGGLE, NetIcons.forBluetooth(if (s.enabled) NetIcons.BtState.ON else NetIcons.BtState.OFF),
            "Bluetooth", trailing = Trailing.Switch(s.enabled))
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        val content = NetworkPanelState.bluetooth(s.present, s.enabled, s.locationEnabled, s.scanning, sec.all.size)
        if (s.enabled) {
            when {
                s.note == BluetoothController.Note.NEEDS_LOCATION -> {
                    e += PanelEntry.Message("msg:permission", "Allow location access to find new devices.")
                    e += PanelEntry.Row(KEY_PERMISSION, R.drawable.ic_set_check, "Allow location access", trailing = Trailing.Chevron)
                }
                !s.locationEnabled -> {
                    e += PanelEntry.Message("msg:location", "Location is off, so the TV can't find new devices.")
                    e += PanelEntry.Row(KEY_LOCATION, R.drawable.ic_settings, "Location", value = "TV settings", trailing = Trailing.Chevron)
                }
                s.note == BluetoothController.Note.SCAN_FAILED -> e += PanelEntry.Message("msg:scanfail",
                    "Searching didn't start. Try again in a moment.")
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

    /** Sub-view for a paired device: Connect or Disconnect, then Forget. [notice] is the last action's line. */
    fun device(d: BtDeviceModel, notice: String? = null): PanelContent {
        val e = ArrayList<PanelEntry>()
        notice?.let { e += PanelEntry.Message("msg:notice", it) }
        e += if (d.connected) PanelEntry.Row(KEY_DISCONNECT, R.drawable.ic_net_close, "Disconnect")
        else PanelEntry.Row(KEY_CONNECT, R.drawable.ic_set_check, "Connect")
        e += PanelEntry.Row(KEY_FORGET, R.drawable.ic_net_close, "Forget this device")
        if (!d.connected) e += PanelEntry.Message("msg:reconnect", "It also reconnects by itself when switched on.")
        return PanelContent(PanelHeader(kindIcon(d.kind), d.name, if (d.connected) "Connected" else "Paired · not connected"), e)
    }

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
