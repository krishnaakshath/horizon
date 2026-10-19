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
import dev.tvlauncher.R
import dev.tvlauncher.ui.StatusModel.BtIcon
import dev.tvlauncher.ui.StatusModel.NetIcon

/**
 * Live network/Bluetooth state while started: a default-network callback (Wi-Fi / Ethernet /
 * offline transitions), broadcasts for RSSI, Wi-Fi radio and Bluetooth state/connection changes,
 * and a 30 s safety timer. [start] in onStart, [stop] in onStop.
 */
class StatusIcons(private val context: Context, private val onChange: (NetIcon, BtIcon) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var lastNet: NetIcon? = null
    private var lastBt: BtIcon? = null
    private val refreshNow = Runnable { refresh() }
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, REFRESH_MS)
        }
    }

    fun start() {
        lastNet = null; lastBt = null
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
        val net = StatusModel.iconFor(netKind(), wifiLevel())
        val bt = bluetooth()
        if (net == lastNet && bt == lastBt) return
        lastNet = net; lastBt = bt
        onChange(net, bt)
    }

    private fun netKind(): StatusModel.NetKind {
        val caps = try { cm?.getNetworkCapabilities(cm.activeNetwork) } catch (e: RuntimeException) { null }
        return StatusModel.netKind(
            hasNetwork = caps != null,
            ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            wifiEnabled = try { wifiManager()?.isWifiEnabled == true } catch (e: RuntimeException) { false }
        )
    }

    private fun wifiManager() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @Suppress("DEPRECATION") // getConnectionInfo is the rssi source below API 31.
    private fun wifiLevel(): Int = try {
        StatusModel.wifiLevel(wifiManager()?.connectionInfo?.rssi ?: Int.MIN_VALUE)
    } catch (e: RuntimeException) {
        0
    }

    @SuppressLint("MissingPermission") // BLUETOOTH is declared (normal permission, targetSdk 30).
    @Suppress("DEPRECATION")
    private fun bluetooth(): BtIcon = try {
        val a = BluetoothAdapter.getDefaultAdapter()
        val on = a?.isEnabled == true
        val connected = on && PROFILES.any { p ->
            try { a!!.getProfileConnectionState(p) == BluetoothProfile.STATE_CONNECTED } catch (e: RuntimeException) { false }
        }
        StatusModel.bluetoothIcon(a != null, on, connected)
    } catch (e: RuntimeException) {
        BtIcon.HIDDEN
    }

    companion object {
        const val REFRESH_MS = 30_000L
        /** A2DP, HEADSET and HID host (4; the constant is hidden below API 28). */
        private val PROFILES = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 4)

        fun drawable(icon: NetIcon): Int = when (icon) {
            NetIcon.WIFI_0 -> R.drawable.ic_wifi_0
            NetIcon.WIFI_1 -> R.drawable.ic_wifi_1
            NetIcon.WIFI_2 -> R.drawable.ic_wifi_2
            NetIcon.WIFI_3 -> R.drawable.ic_wifi_3
            NetIcon.WIFI_OFF -> R.drawable.ic_wifi_off
            NetIcon.ETHERNET -> R.drawable.ic_ethernet
            NetIcon.OFFLINE -> R.drawable.ic_offline
        }

        fun drawable(icon: BtIcon): Int = when (icon) {
            BtIcon.CONNECTED -> R.drawable.ic_bluetooth_connected
            else -> R.drawable.ic_bluetooth
        }
    }
}
