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

data class NotificationChip(val app: String, val title: String?)

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
class NotificationFeed {
    private val lock = Any()
    private val items = ArrayList<NotifInfo>(MAX)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun post(n: NotifInfo) {
        val changed = synchronized(lock) {
            val removed = items.removeAll { it.key == n.key }
            if (!NotificationFilter.accept(n)) return@synchronized removed
            val stored = if (n.visibility == NotifInfo.VISIBILITY_PUBLIC) n else n.copy(title = null, text = null)
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

    /** Up to [max] chips; [appNameOnly] drops titles entirely. */
    fun chips(appNameOnly: Boolean, max: Int = MAX_CHIPS): List<NotificationChip> = snapshot().take(max).map { n ->
        val app = n.appLabel.trim().ifBlank { n.pkg }
        val title = when {
            appNameOnly -> null
            n.visibility != NotifInfo.VISIBILITY_PUBLIC -> GENERIC_TITLE
            else -> n.title?.trim()?.takeIf { it.isNotEmpty() } ?: n.text?.trim()?.takeIf { it.isNotEmpty() } ?: GENERIC_TITLE
        }
        NotificationChip(app, title)
    }

    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun notifyListeners() { for (l in listeners) l() }

    companion object {
        const val MAX = 5
        const val MAX_CHIPS = 3
        const val GENERIC_TITLE = "Notification"

        /** Process-wide feed shared by [AmbientNotificationService] and [AmbientView]. */
        val shared = NotificationFeed()
    }
}
