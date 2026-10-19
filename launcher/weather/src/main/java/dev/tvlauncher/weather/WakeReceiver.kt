package dev.tvlauncher.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wake-from-standby entry point. Exported (see the manifest), so it accepts exactly one action
 * and nothing from the intent's extras; the permission-guarded launcher request has its own
 * receiver.
 */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TriggerActions.TCL_WAKE) return
        Refresher.requestIfStale(context)
    }
}
