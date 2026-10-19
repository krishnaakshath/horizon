package dev.tvlauncher.network

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.tvlauncher.ui.SystemActions

/** Guarded jumps into the TV's own settings screens; each tries a chain of fallbacks. */
object NetworkSettings {
    const val LOCATION_PERMISSION = android.Manifest.permission.ACCESS_FINE_LOCATION

    /**
     * Shows Android's own permission dialog for [LOCATION_PERMISSION] (or, once Android stops
     * showing it, the launcher's app page in the TV settings). False when nothing could open.
     */
    fun requestLocation(context: Context): Boolean =
        dev.tvlauncher.setup.SetupStatus.requestPermission(context, LOCATION_PERMISSION)

    fun openLocation(context: Context): Boolean =
        SystemActions.start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) ||
            SystemActions.start(context, Intent(Settings.ACTION_SETTINGS))

    fun openWifi(context: Context): Boolean =
        SystemActions.start(context, Intent(Settings.ACTION_WIFI_SETTINGS)) ||
            SystemActions.start(context, Intent(Settings.ACTION_WIRELESS_SETTINGS)) ||
            SystemActions.start(context, Intent(Settings.ACTION_SETTINGS))

    fun openBluetooth(context: Context): Boolean =
        SystemActions.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) ||
            SystemActions.start(context, Intent(Settings.ACTION_SETTINGS))
}
