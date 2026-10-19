package dev.tvlauncher.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedules background refresh after boot and after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RefreshScheduler.schedulePeriodic(context, force = true)
            RefreshScheduler.refreshSoon(context)
        }
    }
}
