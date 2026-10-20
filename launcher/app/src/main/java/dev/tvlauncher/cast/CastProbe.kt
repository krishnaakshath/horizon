package dev.tvlauncher.cast

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

/**
 * Read-only look at the TV's own receivers. Nothing here changes a setting or starts another app.
 */
object CastProbe {
    fun state(context: Context): CastModel.State {
        val app = context.applicationContext
        return CastModel.State(
            tvName = CastModel.tvName(deviceName(app), bluetoothName()),
            castReceiver = installed(app, CastModel.CAST_RECEIVER_PACKAGE),
            airPlayInput = airPlayInput(app),
            onNetwork = onNetwork(app),
            receiverAppInstalled = installed(app, CastModel.RECEIVER_APP_PACKAGE)
        )
    }

    private fun deviceName(context: Context): String? = try {
        Settings.Global.getString(context.contentResolver, "device_name")
    } catch (e: Throwable) { null }

    @Suppress("MissingPermission")
    private fun bluetoothName(): String? = try {
        BluetoothAdapter.getDefaultAdapter()?.name
    } catch (e: Throwable) { null }

    fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: Throwable) { false }

    /** The id of the TV input that is the maker's AirPlay receiver, or null. */
    fun airPlayInput(context: Context): String? = try {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        tim?.tvInputList.orEmpty().firstOrNull { info ->
            val label = try { (info.loadCustomLabel(context) ?: info.loadLabel(context))?.toString() } catch (t: Throwable) { null }
            CastModel.isAirPlayInput(info.id, label)
        }?.id
    } catch (e: Throwable) { null }

    private fun onNetwork(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    } catch (e: Throwable) { false }
}
