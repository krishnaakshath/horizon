package dev.tvlauncher.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedules background refresh after boot and after the app is updated; boot also refreshes if the data is stale. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RefreshScheduler.schedulePeriodic(context, force = true)
            if (a == Intent.ACTION_BOOT_COMPLETED) Refresher.requestIfStale(context)
            else RefreshScheduler.refreshSoon(context)
        }
    }
}
