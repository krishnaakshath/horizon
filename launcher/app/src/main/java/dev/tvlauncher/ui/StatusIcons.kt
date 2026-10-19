package dev.tvlauncher.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import dev.tvlauncher.network.NetIcons
import dev.tvlauncher.network.WifiConnection

/**
 * Live network/Bluetooth state for the top-bar pills (and the ambient screen) while started: a default-network callback (Wi-Fi / Ethernet /
 * offline transitions), broadcasts for RSSI, Wi-Fi radio and Bluetooth state/connection changes,
 * and a 30 s safety timer. [start] in onStart, [stop] in onStop. Emits a [StatusModel.Status];
 * hosts map it with StatusModel.networkPillIcon / bluetoothPillIcon, which call the same
 * NetIcons functions as the Wi-Fi / Bluetooth panel headers.
 */
class StatusIcons(private val context: Context, private val onChange: (StatusModel.Status) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var last: StatusModel.Status? = null
    private val refreshNow = Runnable { refresh() }
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, REFRESH_MS)
        }
    }

    fun start() {
        last = null
        refresh()
        main.removeCallbacks(tick)
        main.postDelayed(tick, REFRESH_MS)
        registerNetworkCallback()
        registerReceiver()
    }

    fun stop() {
        main.removeCallbacks(tick)
        main.removeCallbacks(refreshNow)
        callback?.let { cb -> try { cm?.unregisterNetworkCallback(cb) } catch (e: RuntimeException) { } }
        callback = null
        receiver?.let { r -> try { context.unregisterReceiver(r) } catch (e: IllegalArgumentException) { } }
        receiver = null
    }

    /** Coalesces bursts of callbacks (binder threads) into one main-thread refresh. */
    private fun schedule() {
        main.removeCallbacks(refreshNow)
        main.postDelayed(refreshNow, 150)
    }

    private fun registerNetworkCallback() {
        if (callback != null || cm == null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedule()
            override fun onLost(network: Network) = schedule()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = schedule()
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            callback = cb
        } catch (e: RuntimeException) {
            // Broadcasts and the timer still keep the icons fresh.
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = schedule()
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        try {
            context.registerReceiver(r, filter)
            receiver = r
        } catch (e: RuntimeException) {
        }
    }

    fun refresh() {
        val wm = wifiManager()
        val kind = netKind()
        val next = StatusModel.status(
            kind = kind,
            rawRssi = if (kind == WifiConnection.Kind.WIFI) wifiRssi(wm) else null,
            wifiEnabled = try { wm?.isWifiEnabled == true } catch (e: RuntimeException) { false },
            bt = bluetooth()
        )
        if (next == last) return
        last = next
        onChange(next)
    }

    private fun netKind(): WifiConnection.Kind {
        val caps = try { cm?.getNetworkCapabilities(cm.activeNetwork) } catch (e: RuntimeException) { null }
        return StatusModel.connectionKind(
            hasNetwork = caps != null,
            ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        )
    }

    private fun wifiManager() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    /** Raw dBm; StatusModel.status validates and buckets it exactly like the panel header. */
    @Suppress("DEPRECATION") // getConnectionInfo is the rssi source below API 31.
    private fun wifiRssi(wm: WifiManager?): Int? = try {
        wm?.connectionInfo?.rssi
    } catch (e: RuntimeException) {
        null
    }

    /**
     * getProfileConnectionState returns a *connection* state, so it is compared with
     * BluetoothAdapter.STATE_CONNECTED (not BluetoothProfile's profile-state constant).
     */
    @SuppressLint("MissingPermission") // BLUETOOTH is declared (normal permission, targetSdk 30).
    @Suppress("DEPRECATION")
    private fun bluetooth(): NetIcons.BtState? = try {
        val a = BluetoothAdapter.getDefaultAdapter()
        val on = a?.isEnabled == true
        val connected = on && a != null && (isConnected(a, BluetoothProfile.A2DP) ||
            isConnected(a, BluetoothProfile.HEADSET) || isConnected(a, HID_HOST))
        StatusModel.btState(a != null, on, connected)
    } catch (e: RuntimeException) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun isConnected(a: BluetoothAdapter, profile: Int): Boolean = try {
        a.getProfileConnectionState(profile) == BluetoothAdapter.STATE_CONNECTED
    } catch (e: RuntimeException) {
        false
    }

    companion object {
        const val REFRESH_MS = 30_000L
        /** BluetoothProfile.HID_HOST; the constant is hidden below API 28. */
        private const val HID_HOST = 4
    }
}
