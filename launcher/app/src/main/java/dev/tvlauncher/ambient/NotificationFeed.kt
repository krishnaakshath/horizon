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

/** Pure chip model used by AmbientView, so the settings -> rendered text path is testable. */
object AmbientViewModel {
    private const val SEP = "  "

    fun chipsFor(settings: WallpaperSettings, feed: NotificationFeed, accessGranted: Boolean,
                 nowMs: Long = System.currentTimeMillis(), max: Int = NotificationFeed.MAX_CHIPS): List<NotificationChip> =
        if (!settings.notificationsEnabled || !accessGranted) emptyList()
        else feed.chips(settings.showNotificationText, max, nowMs)

    /** "App  Title  5m" (missing parts are omitted). */
    fun chipText(c: NotificationChip): String = buildString {
        append(c.app)
        if (c.title != null) append(SEP).append(c.title)
        if (c.time.isNotEmpty()) append(SEP).append(c.time)
    }

    /** start..endExclusive of the secondary-colored title part in [chipText], or null. */
    fun titleRange(c: NotificationChip): IntRange? =
        c.title?.let { c.app.length..(c.app.length + SEP.length + it.length) }
}

/**
 * Pure user-facing text for granting listener access. Never a command line: a TV without the
 * access screen gets one calm line, and the one-time setup helper (or setup's own automatic
 * grant) switches access on.
 */
object NotificationAccessText {
    /** The one calm line shown when the TV has no access screen of its own. */
    const val SETUP_LINE = dev.tvlauncher.setup.SetupCopy.HELPER_LINE

    fun instructions(settingsOpened: Boolean): String =
        dev.tvlauncher.setup.SetupCopy.notificationAccessHint(settingsOpened)

    @Deprecated("The hint no longer takes a recipe", ReplaceWith("instructions(settingsOpened)"))
    @Suppress("UNUSED_PARAMETER")
    fun instructions(settingsOpened: Boolean, adbLine: String): String = instructions(settingsOpened)
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
     * Up to [max] chips. The single decision point for content: PUBLIC && [showText] shows the
     * title/text; everything else (toggle off, private, secret) is app name (+ age) only.
     */
    fun chips(showText: Boolean, max: Int = MAX_CHIPS,
              nowMs: Long = System.currentTimeMillis()): List<NotificationChip> = snapshot().take(max).map { n ->
        val app = n.appLabel.trim().ifBlank { n.pkg }.take(MAX_CHARS)
        val t = n.title?.trim()?.takeIf { it.isNotEmpty() }
        val x = n.text?.trim()?.takeIf { it.isNotEmpty() }
        val title = when {
            !showText || n.visibility != NotifInfo.VISIBILITY_PUBLIC -> null
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
