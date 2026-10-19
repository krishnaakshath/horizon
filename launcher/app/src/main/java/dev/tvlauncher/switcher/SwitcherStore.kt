package dev.tvlauncher.switcher

import android.content.Context
import android.content.SharedPreferences

/**
 * The Recent apps strip's settings and its own records: the launch log (the fallback list when
 * usage access is missing) and apps closed from the strip. Every call swallows errors.
 */
class SwitcherStore(context: Context) {
    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    } catch (e: Throwable) {
        null
    }

    var doubleHome: Boolean
        get() = prefs?.getBoolean(KEY_DOUBLE_HOME, true) ?: true
        set(v) { edit { putBoolean(KEY_DOUBLE_HOME, v) } }

    /** The controller's Y button opens the strip (Start still opens Spotlight). */
    var padY: Boolean
        get() = prefs?.getBoolean(KEY_PAD_Y, false) ?: false
        set(v) { edit { putBoolean(KEY_PAD_Y, v) } }

    var count: Int
        get() = (prefs?.getInt(KEY_COUNT, RecentsOrder.DEFAULT_COUNT) ?: RecentsOrder.DEFAULT_COUNT)
            .takeIf { it in RecentsOrder.COUNTS } ?: RecentsOrder.DEFAULT_COUNT
        set(v) { edit { putInt(KEY_COUNT, v) } }

    fun log(): List<RecentUse> = RecentsLog.decode(prefs?.getString(KEY_LOG, null))

    fun closed(): Map<String, Long> = RecentsLog.decodeClosed(prefs?.getString(KEY_CLOSED, null))

    @Synchronized
    fun record(pkg: String, at: Long = System.currentTimeMillis()) {
        val next = RecentsLog.record(log(), pkg, at)
        edit { putString(KEY_LOG, RecentsLog.encode(next)) }
    }

    /** Merges system-reported uses into the log, so the list survives usage access being taken away. */
    @Synchronized
    fun remember(uses: List<RecentUse>) {
        var log = log()
        for (u in uses.sortedBy { it.at }) {
            if ((log.firstOrNull { it.pkg == u.pkg }?.at ?: 0L) < u.at) log = RecentsLog.record(log, u.pkg, u.at)
        }
        edit { putString(KEY_LOG, RecentsLog.encode(log.sortedByDescending { it.at })) }
    }

    @Synchronized
    fun markClosed(pkg: String, at: Long = System.currentTimeMillis()) {
        val m = closed().toMutableMap()
        m[pkg] = at
        val trimmed = m.entries.sortedByDescending { it.value }.take(RecentsLog.MAX).associate { it.key to it.value }
        edit {
            putString(KEY_CLOSED, RecentsLog.encodeClosed(trimmed))
            putString(KEY_LOG, RecentsLog.encode(RecentsLog.remove(log(), pkg)))
        }
    }

    /** Uses at or before this time are not listed ("Clear the list"). */
    val clearedAt: Long get() = prefs?.getLong(KEY_CLEARED, 0L) ?: 0L

    fun forget(now: Long = System.currentTimeMillis()) = edit { remove(KEY_LOG); remove(KEY_CLOSED); putLong(KEY_CLEARED, now) }

    private inline fun edit(f: SharedPreferences.Editor.() -> Unit) {
        try { prefs?.edit()?.apply { f() }?.apply() } catch (e: Throwable) { }
    }

    companion object {
        private const val FILE = "switcher"
        private const val KEY_DOUBLE_HOME = "double_home"
        private const val KEY_PAD_Y = "pad_y"
        private const val KEY_COUNT = "count"
        private const val KEY_LOG = "log"
        private const val KEY_CLOSED = "closed"
        private const val KEY_CLEARED = "cleared_at"
    }
}
