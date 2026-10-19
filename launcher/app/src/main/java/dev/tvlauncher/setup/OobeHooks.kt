package dev.tvlauncher.setup

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.tvlauncher.network.NetworkSettings

/**
 * The network page's plug-in point. The launcher's own Wi-Fi component registers itself with
 * [OobeHooks.network] once it exists; until then [SystemNetworkSetup] opens the TV's own Wi-Fi
 * screen (only when it resolves) and reads the connection state.
 */
interface OobeNetwork {
    /** Connected to a network that has internet (or validation is unknown). */
    fun isOnline(context: Context): Boolean

    /** One short line, e.g. "Connected (Wi-Fi)"; null when unknown. */
    fun status(context: Context): String?

    /** Shows a way to connect; false when there is none on this TV. */
    fun open(activity: Activity): Boolean
}

object SystemNetworkSetup : OobeNetwork {
    private fun caps(context: Context): NetworkCapabilities? = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.getNetworkCapabilities(cm.activeNetwork)
    } catch (e: Throwable) {
        null
    }

    override fun isOnline(context: Context): Boolean =
        caps(context)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    override fun status(context: Context): String? {
        val c = caps(context) ?: return "Not connected"
        return when {
            c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected (cable)"
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected (Wi-Fi)"
            else -> "Connected"
        }
    }

    override fun open(activity: Activity): Boolean = NetworkSettings.openWifi(activity)
}

object OobeHooks {
    @Volatile var network: OobeNetwork = SystemNetworkSetup
}
