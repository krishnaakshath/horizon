package dev.tvlauncher.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.Inet4Address

data class WifiConnection(
    val kind: Kind,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val linkMbps: Int,
    val frequencyMhz: Int,
    val ip: String?
) {
    enum class Kind { WIFI, ETHERNET, NONE }

    val level: Int get() = rssi?.let { WifiNetworkModel.levelOf(it) } ?: 0
    val band: WifiBand get() = WifiNetworkModel.bandOf(frequencyMhz)

    companion object { val NONE = WifiConnection(Kind.NONE, null, null, null, 0, 0, null) }
}

data class WifiSnapshot(
    val connection: WifiConnection,
    val wifiEnabled: Boolean,
    val hasLocationPermission: Boolean,
    val scanning: Boolean,
    val scans: List<RawScan>,
    /** > 0 when Android's scan throttle (4 per 2 min) is holding a new scan back. */
    val throttledSeconds: Int
)

/**
 * Current connection (Wi-Fi or Ethernet) plus cached/fresh scan results. Every listener call is on
 * the main thread. [start] in onStart, [stop] in onStop; both are idempotent. Never throws: a
 * missing permission shows up as [WifiSnapshot.hasLocationPermission] == false.
 *
 * Platform limits: apps targeting API 29+ cannot join networks or toggle Wi-Fi (the panel opens the
 * TV's Wi-Fi screen instead); scan results need ACCESS_FINE_LOCATION (ADB grant) and startScan is
 * throttled to 4 per 2 minutes.
 */
@Suppress("DEPRECATION")
class WifiController(context: Context) {
    fun interface Listener { fun onChanged(snapshot: WifiSnapshot) }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wm = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val listeners = LinkedHashSet<Listener>()
    private val throttle = ScanThrottle()
    private var scanning = false
    private var started = false
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var throttledSeconds = 0

    var snapshot: WifiSnapshot = build()
        private set

    val isStarted: Boolean get() = started

    private val refreshNow = Runnable { publish() }
    private val scanTimeout = Runnable { scanning = false; publish() }

    fun addListener(l: Listener) { listeners.add(l); l.onChanged(snapshot) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun start() {
        if (started) return
        started = true
        registerCallback()
        registerReceiver()
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(refreshNow)
        main.removeCallbacks(scanTimeout)
        scanning = false
        callback?.let { try { cm?.unregisterNetworkCallback(it) } catch (e: RuntimeException) { } }
        callback = null
        receiver?.let { try { app.unregisterReceiver(it) } catch (e: IllegalArgumentException) { } }
        receiver = null
    }

    fun hasLocationPermission(): Boolean =
        app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun refresh() = publish()

    /** Ask the system for a fresh scan; cached results stay visible. Safe to call any time. */
    fun scan() {
        throttledSeconds = 0
        if (!hasLocationPermission() || wm == null || !wm.isWifiEnabledSafe()) { publish(); return }
        val now = SystemClock.elapsedRealtime()
        if (!throttle.tryAcquire(now)) {
            throttledSeconds = throttle.retryAfterSeconds(now)
            publish()
            return
        }
        val ok = try { wm.startScan() } catch (e: SecurityException) { false } catch (e: RuntimeException) { false }
        if (ok) {
            scanning = true
            main.removeCallbacks(scanTimeout)
            main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
        } else {
            // The system refused (its own throttle); fall back to whatever it has cached.
            throttledSeconds = 120
        }
        publish()
    }

    private fun WifiManager.isWifiEnabledSafe() = try { isWifiEnabled } catch (e: RuntimeException) { false }

    private fun publish() {
        snapshot = build()
        for (l in listeners.toList()) l.onChanged(snapshot)
    }

    private fun schedule() {
        main.removeCallbacks(refreshNow)
        main.postDelayed(refreshNow, 150)
    }

    private fun registerCallback() {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedule()
            override fun onLost(network: Network) = schedule()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = schedule()
        }
        try {
            cm?.registerDefaultNetworkCallback(cb)
            callback = cb
        } catch (e: RuntimeException) {
            callback = null
        }
    }

    private fun registerReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    scanning = false
                    main.removeCallbacks(scanTimeout)
                }
                schedule()
            }
        }
        val f = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        try {
            app.registerReceiver(r, f, null, main)
            receiver = r
        } catch (e: RuntimeException) {
            receiver = null
        }
    }

    private fun build(): WifiSnapshot {
        val perm = hasLocationPermission()
        val enabled = wm?.isWifiEnabledSafe() ?: false
        return WifiSnapshot(
            connection = readConnection(),
            wifiEnabled = enabled,
            hasLocationPermission = perm,
            scanning = scanning,
            scans = if (perm && enabled) readScans() else emptyList(),
            throttledSeconds = throttledSeconds
        )
    }

    @SuppressLint("MissingPermission")
    private fun readScans(): List<RawScan> = try {
        wm?.scanResults.orEmpty().map { RawScan(it.SSID, it.BSSID, it.level, it.frequency, it.capabilities) }
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }

    private fun readConnection(): WifiConnection = try {
        val net = cm?.activeNetwork
        val caps = net?.let { cm?.getNetworkCapabilities(it) }
        val ip = net?.let { ipOf(it) }
        when {
            caps == null -> WifiConnection.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                WifiConnection(WifiConnection.Kind.ETHERNET, null, null, null, 0, 0, ip)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val info = wm?.connectionInfo
                val ssid = info?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
                val bssid = info?.bssid?.takeUnless { it == "02:00:00:00:00:00" }
                val rssi = info?.rssi?.takeIf { it in -126..0 }
                val legacyIp = info?.ipAddress?.takeIf { it != 0 }?.let {
                    "${it and 0xff}.${it shr 8 and 0xff}.${it shr 16 and 0xff}.${it shr 24 and 0xff}"
                }
                WifiConnection(
                    WifiConnection.Kind.WIFI, ssid, bssid, rssi,
                    info?.linkSpeed?.coerceAtLeast(0) ?: 0, info?.frequency?.coerceAtLeast(0) ?: 0, ip ?: legacyIp
                )
            }
            else -> WifiConnection.NONE
        }
    } catch (e: RuntimeException) {
        WifiConnection.NONE
    }

    private fun ipOf(net: Network): String? = try {
        cm?.getLinkProperties(net)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: RuntimeException) {
        null
    }

    private companion object { const val SCAN_TIMEOUT_MS = 10_000L }
}
