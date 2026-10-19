package dev.tvlauncher.speed

import android.content.Context

/** SharedPreferences "speed": the owner's switches, the schedule, the run history and the last result. */
class SpeedPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var autoEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO, true)
        set(v) { sp.edit().putBoolean(KEY_AUTO, v).apply() }

    var freeOnHome: Boolean
        get() = sp.getBoolean(KEY_HOME, true)
        set(v) { sp.edit().putBoolean(KEY_HOME, v).apply() }

    var quietHour: Int
        get() = SpeedSchedule.normalizeHour(sp.getInt(KEY_HOUR, SpeedSchedule.DEFAULT_HOUR))
        set(v) { sp.edit().putInt(KEY_HOUR, SpeedSchedule.normalizeHour(v)).apply() }

    var everyDays: Int
        get() = SpeedSchedule.normalizeEvery(sp.getInt(KEY_EVERY, SpeedSchedule.DEFAULT_EVERY))
        set(v) { sp.edit().putInt(KEY_EVERY, SpeedSchedule.normalizeEvery(v)).apply() }

    /** Apps the owner never wants closed (in addition to the built-in rules). */
    var keep: Set<String>
        get() = ComponentLists.parseOwner(sp.getString(KEY_KEEP, null))
        set(v) { sp.edit().putString(KEY_KEEP, ComponentLists.formatOwner(v)).apply() }

    val lastAutoRun: Long get() = sp.getLong(KEY_LAST_AUTO, 0L)

    fun history(): List<Long> = longs(KEY_HISTORY)

    fun unattended(): List<Long> = longs(KEY_UNATTENDED)

    /** Synchronous: the alarm receiver's process may be gone right after. */
    fun recordStart(t: Trigger, now: Long) {
        val e = sp.edit().putString(KEY_HISTORY, RateLimit.record(history(), now).joinToString(","))
        if (t != Trigger.MANUAL) e.putString(KEY_UNATTENDED, RateLimit.record(unattended(), now).joinToString(","))
        if (t == Trigger.AUTO) e.putLong(KEY_LAST_AUTO, now)
        e.commit()
    }

    fun saveResult(r: CleanupResult) {
        sp.edit()
            .putString(KEY_RESULT, listOf(r.trigger.name, r.storageFreed, r.memoryFreed, r.ownCacheBytes, r.appsClosed,
                if (r.closingSkipped) 1 else 0, r.at).joinToString(","))
            .commit()
    }

    fun lastResult(): CleanupResult? = try {
        sp.getString(KEY_RESULT, null)?.split(',')?.takeIf { it.size == 7 }?.let { c ->
            CleanupResult(Trigger.valueOf(c[0]), c[1].toLong(), c[2].toLong(), c[3].toLong(), c[4].toInt(), c[5] == "1", c[6].toLong())
        }
    } catch (e: Throwable) {
        null
    }

    private fun longs(key: String): List<Long> =
        sp.getString(key, null).orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    companion object {
        const val NAME = "speed"
        private const val KEY_AUTO = "auto"
        private const val KEY_HOME = "free_on_home"
        private const val KEY_HOUR = "quiet_hour"
        private const val KEY_EVERY = "every_days"
        private const val KEY_KEEP = "keep"
        private const val KEY_LAST_AUTO = "last_auto"
        private const val KEY_HISTORY = "history"
        private const val KEY_UNATTENDED = "unattended"
        private const val KEY_RESULT = "last_result"
    }
}
