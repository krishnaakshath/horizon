package dev.tvlauncher.helper.volume

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import dev.tvlauncher.helper.HelperService

/**
 * The launcher's Settings > Volume & output asks here (ordered broadcast, same signer only via
 * dev.tvlauncher.permission.VOLUME_BAR): optionally sets the "Use launcher volume bar" switch
 * ([EXTRA_SET_ENABLED]) and the launcher's animation level ([EXTRA_MOTION]), and answers with
 * the switch ([setResultCode] 1/0) plus whether the service is running ([EXTRA_BOUND]).
 */
class VolumeBarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val prefs = VolumePrefs(context)
        if (intent.hasExtra(EXTRA_SET_ENABLED)) prefs.enabled = intent.getBooleanExtra(EXTRA_SET_ENABLED, true)
        intent.getStringExtra(EXTRA_MOTION)?.takeIf { it in setOf("full", "lite", "off") }?.let { prefs.launcherMotion = it }
        val extras = Bundle().apply {
            putBoolean(EXTRA_BOUND, HelperService.current != null)
            putBoolean(EXTRA_SERVICE_ON, serviceEnabled(context))
            putInt(EXTRA_KEYS_SEEN, prefs.keysSeen)
            putInt(EXTRA_KEYS_TAKEN, prefs.keysTaken)
        }
        setResult(if (prefs.enabled) 1 else 0, null, extras)
    }

    companion object {
        const val ACTION = "dev.tvlauncher.helper.action.VOLUME_BAR"
        const val EXTRA_SET_ENABLED = "set_enabled"
        const val EXTRA_MOTION = "motion"
        const val EXTRA_BOUND = "bound"
        const val EXTRA_SERVICE_ON = "service_on"
        const val EXTRA_KEYS_SEEN = "keys_seen"
        const val EXTRA_KEYS_TAKEN = "keys_taken"

        fun serviceEnabled(context: Context): Boolean = try {
            val me = ComponentName(context, HelperService::class.java)
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == me } == true
        } catch (e: Throwable) {
            false
        }
    }
}
