package dev.tvlauncher.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Keeps the phone page's server running while "Phone remote" is on (a foreground service, so the
 * TV does not stop it in the background). Not exported: started only by this app.
 */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!LinkStore(this).enabled) { stopSelf(); return START_NOT_STICKY }
        try { startForeground(NOTIFICATION_ID, notification()) } catch (e: Exception) { }
        if (!LinkRuntime.startServer(this)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        LinkRuntime.stopServer()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, LinkBrand.APP_NAME, NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return b.setContentTitle(LinkBrand.APP_NAME).setContentText("Phone remote is on")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL = "link"
        private const val NOTIFICATION_ID = 8765

        fun sync(context: Context) {
            val i = Intent(context, LinkService::class.java)
            try {
                if (LinkStore(context).enabled) {
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
                } else {
                    context.stopService(i)
                    LinkRuntime.stopServer()
                }
            } catch (e: Exception) {
            }
        }
    }
}

/** Boot and app update: restart the server when the owner left "Phone remote" on. System-only broadcasts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LinkService.sync(context)
        }
    }
}

/**
 * `tvtool provision` switches the phone remote on: `adb shell am broadcast -a
 * dev.tvlauncher.link.action.PROVISION_START -n dev.tvlauncher.link/.ProvisionStartReceiver`.
 * Exported, but only senders holding WRITE_SECURE_SETTINGS (a signature|privileged permission the
 * adb shell has and ordinary apps cannot get) reach it. The owner can switch it off again in
 * Settings > System > Phone remote.
 */
class ProvisionStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != dev.tvlauncher.link.core.LinkContract.ACTION_PROVISION_START) return
        LinkStore(context).enabled = true
        LinkService.sync(context)
        if (isOrderedBroadcast) resultCode = 1
    }
}
