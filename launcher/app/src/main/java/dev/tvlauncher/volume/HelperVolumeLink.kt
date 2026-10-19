package dev.tvlauncher.volume

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dev.tvlauncher.appsettings.ModuleProbe

/**
 * Talks to Launcher Helper's volume bar (helper volume/VolumeBarReceiver) with an ordered
 * broadcast that only the same signer may send or receive (dev.tvlauncher.permission.VOLUME_BAR).
 * It answers even when its accessibility service is not running, which is how we know whether the
 * bar works in other apps right now.
 */
object HelperVolumeLink {
    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
    const val ACTION = "dev.tvlauncher.helper.action.VOLUME_BAR"
    private const val NO_ANSWER = -1
    private const val TIMEOUT_MS = 1500L

    /** [setEnabled] null only asks. [onResult] runs once on the main thread (also on timeout). */
    fun query(context: Context, setEnabled: Boolean?, motion: String?, onResult: (Boolean?, VolumeCopy.HelperState) -> Unit) {
        val installed = try { ModuleProbe.isAppInstalled(context, HELPER_PACKAGE) } catch (e: Throwable) { false }
        val main = Handler(Looper.getMainLooper())
        if (!installed) {
            main.post { onResult(null, VolumeCopy.HelperState.NOT_INSTALLED) }
            return
        }
        var done = false
        fun finish(enabled: Boolean?, s: VolumeCopy.HelperState) {
            if (done) return
            done = true
            onResult(enabled, s)
        }
        val timeout = Runnable { finish(null, VolumeCopy.HelperState.NOT_RUNNING) }
        main.postDelayed(timeout, TIMEOUT_MS)
        val intent = Intent(ACTION).setPackage(HELPER_PACKAGE).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        setEnabled?.let { intent.putExtra("set_enabled", it) }
        motion?.let { intent.putExtra("motion", it) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                main.removeCallbacks(timeout)
                val code = resultCode
                val x = getResultExtras(false)
                if (code == NO_ANSWER || x == null) {
                    finish(null, VolumeCopy.HelperState.NOT_RUNNING)
                } else {
                    finish(code == 1, VolumeCopy.HelperState(true, true, x.getBoolean("bound"), x.getBoolean("service_on")))
                }
            }
        }
        try {
            val app = if (context is Activity) context.applicationContext else context
            app.sendOrderedBroadcast(intent, null, receiver, main, NO_ANSWER, null, null)
        } catch (e: Throwable) {
            main.removeCallbacks(timeout)
            main.post { finish(null, VolumeCopy.HelperState.NOT_RUNNING) }
        }
    }
}
