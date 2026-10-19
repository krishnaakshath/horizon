package dev.tvlauncher.network

import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.tvlauncher.ui.SystemActions

/** Guarded jumps into the TV's own settings screens; each tries a chain of fallbacks. */
object NetworkSettings {
    const val ADB_LOCATION_GRANT = "adb shell pm grant dev.tvlauncher android.permission.ACCESS_FINE_LOCATION"

    const val ADB_LOCATION_ON = "adb shell settings put secure location_mode 3"

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
