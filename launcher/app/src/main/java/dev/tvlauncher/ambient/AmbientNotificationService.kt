package dev.tvlauncher.ambient

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Opt-in notification listener. Keeps at most 5 recent, non-ongoing, non-media, non-summary
 * notifications in [NotificationFeed.shared] (memory only: never persisted, never logged) and
 * clears them when the listener disconnects or the service is destroyed.
 */
class AmbientNotificationService : NotificationListenerService() {
    private val labels = HashMap<String, String>()

    override fun onListenerConnected() {
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        active.sortedBy { it.postTime }.forEach { post(it) }
    }

    override fun onListenerDisconnected() {
        NotificationFeed.shared.clear()
    }

    override fun onDestroy() {
        NotificationFeed.shared.clear()
        labels.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) post(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let { NotificationFeed.shared.remove(it) }
    }

    private fun post(sbn: StatusBarNotification) {
        try {
            NotificationFeed.shared.post(toInfo(sbn))
        } catch (e: Exception) {
            // Malformed extras from some app: ignore that notification.
        }
    }

    private fun toInfo(sbn: StatusBarNotification): NotifInfo {
        val n = sbn.notification
        val extras = n.extras
        val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val media = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
            template.endsWith("MediaStyle") || n.category == Notification.CATEGORY_TRANSPORT
        val ongoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        return NotifInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            appLabel = label(sbn.packageName),
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            postTime = sbn.postTime,
            ongoing = ongoing,
            isMediaStyle = media,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            visibility = n.visibility
        )
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        } catch (e: Exception) {
            pkg
        }
    }
}

/** One-time user grant for [AmbientNotificationService]. */
object NotificationAccess {
    const val ADB_ALLOW =
        "adb shell cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService"

    fun component(context: Context) = ComponentName(context, AmbientNotificationService::class.java)

    fun isEnabled(context: Context): Boolean = try {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = component(context)
        flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    } catch (e: Exception) {
        false
    }

    /** The system listener-access screen, or null when the TV build does not ship one. */
    fun settingsIntent(context: Context): Intent? {
        val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { if (i.resolveActivity(context.packageManager) != null) i else null } catch (e: Exception) { null }
    }
}
