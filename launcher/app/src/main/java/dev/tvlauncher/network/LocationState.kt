package dev.tvlauncher.network

import android.content.Context
import android.location.LocationManager
import android.os.Build

/** Runs [block]; any Throwable except OutOfMemoryError yields [default]. */
internal inline fun <T> safely(default: T, block: () -> T): T = try {
    block()
} catch (t: Throwable) {
    if (t is OutOfMemoryError) throw t
    default
}

/**
 * Android 9+ returns empty Wi-Fi scan results and no Bluetooth discovery hits while the device-wide
 * Location switch is off, even with the permission granted. Unknown means enabled (never a false alarm).
 */
object LocationState {
    @Suppress("DEPRECATION")
    fun isEnabled(context: Context): Boolean = safely(true) {
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@safely true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Classic Bluetooth discovery: Android 6-8.1 (API 23-27) needs only the runtime permission;
     * from Android 9 (API 28) the device-wide Location services toggle must also be on for
     * ACTION_FOUND results. Below 28 a "Location off" warning would be false.
     */
    fun requiredForBluetoothDiscovery(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.P

    /** Wi-Fi getScanResults needs Location services on every API level this app supports (24+). */
    @Suppress("UNUSED_PARAMETER")
    fun requiredForWifiScan(sdkInt: Int): Boolean = true

    /** True when Bluetooth discovery is not blocked by the Location toggle on this API level. */
    fun bluetoothLocationOk(sdkInt: Int, toggleOn: Boolean): Boolean =
        !requiredForBluetoothDiscovery(sdkInt) || toggleOn

    fun bluetoothLocationOk(context: Context): Boolean =
        bluetoothLocationOk(Build.VERSION.SDK_INT, isEnabled(context))

    const val MODE_CHANGED_ACTION = "android.location.MODE_CHANGED"
}
