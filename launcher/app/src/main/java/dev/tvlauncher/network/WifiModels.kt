package dev.tvlauncher.network

/** One scan result reduced to plain values so the list logic stays JVM-testable. */
data class RawScan(
    val ssid: String?,
    val bssid: String?,
    val levelDbm: Int,
    val frequencyMhz: Int,
    val capabilities: String?
)

enum class WifiBand(val label: String) { GHZ_2_4("2.4 GHz"), GHZ_5("5 GHz"), GHZ_6("6 GHz"), UNKNOWN("") }

enum class WifiSecurity(val label: String, val isOpen: Boolean = false) {
    OPEN("Open", true),
    WEP("WEP"),
    WPA("WPA"),
    WPA2("WPA2"),
    WPA3("WPA3"),
    WPA2_WPA3("WPA2/WPA3"),
    ENTERPRISE("Enterprise")
}

data class WifiNetworkModel(
    val ssid: String,
    val bssid: String,
    val levelDbm: Int,
    val frequencyMhz: Int,
    val security: WifiSecurity,
    val hidden: Boolean,
    val connected: Boolean = false
) {
    val band: WifiBand get() = bandOf(frequencyMhz)
    val signalLevel: Int get() = levelOf(levelDbm)

    companion object {
        const val HIDDEN_NAME = "Hidden network"
        const val MAX_LEVEL = 4
        private const val MIN_RSSI = -100
        private const val MAX_RSSI = -55

        /** Same RSSI window as StatusModel (-100..-55 dBm) split into 5 buckets 0..4. */
        fun levelOf(rssi: Int): Int = when {
            rssi <= MIN_RSSI -> 0
            rssi >= MAX_RSSI -> MAX_LEVEL
            else -> (rssi - MIN_RSSI) * MAX_LEVEL / (MAX_RSSI - MIN_RSSI)
        }

        fun bandOf(mhz: Int): WifiBand = when (mhz) {
            in 2400..2500 -> WifiBand.GHZ_2_4
            in 4900..5900 -> WifiBand.GHZ_5
            in 5925..7125 -> WifiBand.GHZ_6
            else -> WifiBand.UNKNOWN
        }

        fun securityOf(caps: String?): WifiSecurity {
            val c = caps.orEmpty().uppercase()
            val sae = "SAE" in c
            val psk = "PSK" in c
            return when {
                "EAP" in c -> WifiSecurity.ENTERPRISE
                sae && psk -> WifiSecurity.WPA2_WPA3
                sae -> WifiSecurity.WPA3
                "WPA2" in c || "RSN" in c -> WifiSecurity.WPA2
                "WPA" in c -> WifiSecurity.WPA
                "WEP" in c -> WifiSecurity.WEP
                else -> WifiSecurity.OPEN
            }
        }
    }
}

object WifiListBuilder {
    private fun isHidden(ssid: String?) =
        ssid.isNullOrBlank() || ssid == "<unknown ssid>" || ssid == "\"\""

    /**
     * One entry per SSID (strongest wins, but the connected BSSID's entry wins over a stronger
     * sibling), all hidden networks collapsed into one, connected first, rest by signal.
     */
    fun build(results: List<RawScan>, connectedBssid: String?, connectedSsid: String?): List<WifiNetworkModel> {
        val cb = connectedBssid?.lowercase()
        val cs = connectedSsid?.takeUnless { isHidden(it) }
        val byKey = LinkedHashMap<String, RawScan>()
        for (r in results) {
            val hidden = isHidden(r.ssid)
            val key = if (hidden) "\u0000hidden" else r.ssid!!
            val cur = byKey[key]
            byKey[key] = if (cur == null || better(r, cur, cb)) r else cur
        }
        val models = byKey.values.map { r ->
            val hidden = isHidden(r.ssid)
            val isConn = (cb != null && r.bssid?.lowercase() == cb) ||
                (cb == null && cs != null && !hidden && r.ssid == cs)
            WifiNetworkModel(
                ssid = if (hidden) WifiNetworkModel.HIDDEN_NAME else r.ssid!!,
                bssid = r.bssid.orEmpty(),
                levelDbm = r.levelDbm,
                frequencyMhz = r.frequencyMhz,
                security = WifiNetworkModel.securityOf(r.capabilities),
                hidden = hidden,
                connected = isConn
            )
        }
        return models.sortedWith(compareByDescending<WifiNetworkModel> { it.connected }.thenByDescending { it.levelDbm })
    }

    private fun better(a: RawScan, b: RawScan, connectedBssid: String?): Boolean {
        if (connectedBssid != null) {
            val ac = a.bssid?.lowercase() == connectedBssid
            val bc = b.bssid?.lowercase() == connectedBssid
            if (ac != bc) return ac
        }
        return a.levelDbm > b.levelDbm
    }
}
