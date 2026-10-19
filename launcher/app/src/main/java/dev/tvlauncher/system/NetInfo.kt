package dev.tvlauncher.system

import android.content.Context
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import dev.tvlauncher.devices.InputsModel
import dev.tvlauncher.devices.RawInput
import java.net.Inet4Address

/** One read of the active network for Settings > Wi-Fi and Network check. Off the main thread; never throws. */
data class NetInfo(
    val hasNetwork: Boolean = false,
    val wifi: Boolean = false,
    val ethernet: Boolean = false,
    val wifiEnabled: Boolean = false,
    val ssid: String? = null,
    val rssi: Int? = null,
    val linkMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val ip: String? = null,
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
    val validated: Boolean = false,
    val captive: Boolean = false
) {
    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): NetInfo = safe(NetInfo()) {
            val app = context.applicationContext
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wm = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val net = cm?.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            val lp = net?.let { cm.getLinkProperties(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val info = if (isWifi) safe(null) { wm?.connectionInfo } else null
            NetInfo(
                hasNetwork = caps != null,
                wifi = isWifi,
                ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
                wifiEnabled = safe(false) { wm?.isWifiEnabled == true },
                ssid = info?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" },
                rssi = info?.rssi?.takeIf { it in -126..0 },
                linkMbps = info?.linkSpeed?.coerceAtLeast(0) ?: 0,
                frequencyMhz = info?.frequency?.coerceAtLeast(0) ?: 0,
                ip = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress,
                gateway = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress,
                dns = lp?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
                validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                captive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
            )
        }
    }
}

/** Passthrough inputs straight from TvInputManager (plain values for [InputsModel]). */
object InputReader {
    fun read(context: Context): List<RawInput> = safe(emptyList()) {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return@safe emptyList()
        tim.tvInputList.filter { it.isPassthroughInput }.map { info ->
            val state = safe(InputsModel.STATE_DISCONNECTED) { tim.getInputState(info.id) }
            val parent = if (Build.VERSION.SDK_INT >= 26) safe<String?>(null) { info.parentId } else null
            val label = safe("") { (info.loadCustomLabel(context) ?: info.loadLabel(context))?.toString().orEmpty() }
            RawInput(info.id, parent, label, info.type, state)
        }
    }
}
