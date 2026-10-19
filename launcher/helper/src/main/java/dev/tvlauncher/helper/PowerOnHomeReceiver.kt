package dev.tvlauncher.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The launcher's request after the TV turned on (signature permission: only our own launcher can
 * send it). Acts only through the running accessibility service; does nothing when it is off.
 */
class PowerOnHomeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        try {
            val service = HelperService.current
            val done = service?.homeAfterPowerOn()
            android.util.Log.i("PowerOn", if (service == null) "helper: accessibility service not bound" else "helper: HOME sent=$done")
        } catch (e: Throwable) {
        }
    }

    companion object {
        const val ACTION = "dev.tvlauncher.helper.action.HOME_AFTER_POWER_ON"
    }
}
