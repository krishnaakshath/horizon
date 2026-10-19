package dev.tvlauncher.ambient

/** Minimal, framework-free view of a StatusBarNotification. Never persisted or logged. */
data class NotifInfo(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val ongoing: Boolean,
    val isMediaStyle: Boolean,
    val isGroupSummary: Boolean,
    /** Notification.VISIBILITY_* values. */
    val visibility: Int
) {
    companion object {
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_SECRET = -1
    }
}

/** [title] is null for app-name-only chips; [time] is a short age like "5m". */
data class NotificationChip(val app: String, val title: String?, val time: String = "")

object NotificationTime {
    fun short(postTimeMs: Long, nowMs: Long): String {
        if (postTimeMs <= 0) return ""
        val min = (nowMs - postTimeMs).coerceAtLeast(0) / 60_000L
        return when {
            min < 1 -> "now"
            min < 60 -> "${min}m"
            min < 24 * 60 -> "${min / 60}h"
            else -> "${min / (24 * 60)}d"
        }
    }
}

/** Pure user-facing text for granting listener access. */
object NotificationAccessText {
    fun adbLine(pkg: String, serviceClass: String) = "adb shell cmd notification allow_listener $pkg/$serviceClass"

    fun instructions(settingsOpened: Boolean, adbLine: String): String =
        if (settingsOpened) "Turn on TV Launcher in the list that just opened, then press Back."
        else "This TV has no notification access screen. From a computer on the same network, run:\n$adbLine"
}

object NotificationFilter {
    const val OWN_PACKAGE = "dev.tvlauncher"

    fun accept(n: NotifInfo): Boolean =
        !n.ongoing && !n.isMediaStyle && !n.isGroupSummary && n.pkg != OWN_PACKAGE &&
            (!n.title.isNullOrBlank() || !n.text.isNullOrBlank())
}

/**
 * Bounded (5), newest-first, de-duplicated by key, in memory only. Non-public notifications are
 * stored without any title/text so their content never even reaches memory here. Thread-safe:
 * the listener service posts from its binder thread, the UI reads on the main thread.
 */
class NotificationFeed(enabled: Boolean = true) {
    private val lock = Any()
    @Volatile var isEnabled: Boolean = enabled
        private set
    private val items = ArrayList<NotifInfo>(MAX)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** Off: nothing is kept; turning it off clears everything immediately. */
    fun setEnabled(on: Boolean) {
        isEnabled = on
        if (!on) clear()
    }

    fun post(n: NotifInfo) {
        if (!isEnabled) return
        val changed = synchronized(lock) {
            val removed = items.removeAll { it.key == n.key }
            if (!NotificationFilter.accept(n)) return@synchronized removed
            val stored = if (n.visibility == NotifInfo.VISIBILITY_PUBLIC) {
                n.copy(title = n.title?.take(MAX_CHARS), text = n.text?.take(MAX_CHARS))
            } else {
                n.copy(title = null, text = null)
            }
            items.add(0, stored)
            while (items.size > MAX) items.removeAt(items.size - 1)
            true
        }
        if (changed) notifyListeners()
    }

    fun remove(key: String) {
        val changed = synchronized(lock) { items.removeAll { it.key == key } }
        if (changed) notifyListeners()
    }

    fun clear() {
        val changed = synchronized(lock) { val had = items.isNotEmpty(); items.clear(); had }
        if (changed) notifyListeners()
    }

    fun snapshot(): List<NotifInfo> = synchronized(lock) { ArrayList(items) }

    /**
     * Up to [max] chips. [appNameOnly] drops titles entirely; private/secret notifications are
     * always app-name-only; message text appears only when [showText] and the notification is public.
     */
    fun chips(appNameOnly: Boolean, showText: Boolean = false, max: Int = MAX_CHIPS,
              nowMs: Long = System.currentTimeMillis()): List<NotificationChip> = snapshot().take(max).map { n ->
        val app = n.appLabel.trim().ifBlank { n.pkg }.take(MAX_CHARS)
        val t = n.title?.trim()?.takeIf { it.isNotEmpty() }
        val x = n.text?.trim()?.takeIf { it.isNotEmpty() }
        val title = when {
            appNameOnly || n.visibility != NotifInfo.VISIBILITY_PUBLIC -> null
            !showText -> t
            t != null && x != null -> "$t: $x".take(MAX_CHARS)
            else -> t ?: x
        }
        NotificationChip(app, title, NotificationTime.short(n.postTime, nowMs))
    }

    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun notifyListeners() { for (l in listeners) l() }

    companion object {
        const val MAX = 5
        const val MAX_CHIPS = 3
        const val MAX_CHARS = 80

        /** Process-wide feed; disabled until the owner opts in (see WallpaperStore.save). */
        val shared = NotificationFeed(enabled = false)
    }
}
