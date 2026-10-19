package dev.tvlauncher.appsettings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import dev.tvlauncher.ui.StatusModel

/** One-shot status for the shortcut rows. Call off the main thread; never throws. */
internal object NetworkStatusReader {
    data class Status(val wifi: String, val bluetooth: String, val storage: String?)

    fun read(context: Context): Status {
        val app = context.applicationContext
        return Status(wifi(app), bluetooth(), storage(app))
    }

    @Suppress("DEPRECATION") // connectionInfo is the SSID/RSSI source below API 31.
    private fun wifi(context: Context): String = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val caps = try { cm?.getNetworkCapabilities(cm.activeNetwork) } catch (e: RuntimeException) { null }
        val kind = StatusModel.netKind(
            hasNetwork = caps != null,
            ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            wifiEnabled = try { wm?.isWifiEnabled == true } catch (e: RuntimeException) { false }
        )
        val info = if (kind == StatusModel.NetKind.WIFI) try { wm?.connectionInfo } catch (e: RuntimeException) { null } else null
        StatusText.wifi(kind, StatusText.ssid(info?.ssid), StatusModel.wifiLevel(info?.rssi ?: Int.MIN_VALUE))
    } catch (e: RuntimeException) {
        ""
    }

    @SuppressLint("MissingPermission") // BLUETOOTH is declared (normal permission, targetSdk 30).
    @Suppress("DEPRECATION")
    private fun bluetooth(): String = try {
        val a = BluetoothAdapter.getDefaultAdapter()
        val on = a?.isEnabled == true
        val connected = on && intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 4).any { p ->
            try { a!!.getProfileConnectionState(p) == BluetoothAdapter.STATE_CONNECTED } catch (e: RuntimeException) { false }
        }
        StatusText.bluetooth(a != null, on, connected)
    } catch (e: RuntimeException) {
        ""
    }

    private fun storage(context: Context): String? = try {
        val free = StatFs(Environment.getDataDirectory().path).availableBytes
        Formatter.formatShortFileSize(context, free) + " free"
    } catch (e: RuntimeException) {
        null
    }
}
