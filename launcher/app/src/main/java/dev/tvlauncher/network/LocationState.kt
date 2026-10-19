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

    const val MODE_CHANGED_ACTION = "android.location.MODE_CHANGED"
}
