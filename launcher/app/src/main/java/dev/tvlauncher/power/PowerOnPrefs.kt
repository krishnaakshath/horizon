package dev.tvlauncher.power

import android.content.Context

/** SharedPreferences "power_on": the owner's switch (default on), the policy state and the last outcome. */
class PowerOnPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) { sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    fun state(): PowerOnState = try {
        PowerOnState(
            armedAt = sp.getLong(KEY_ARMED, -1L),
            trigger = sp.getString(KEY_TRIGGER, null)?.let { runCatching { PowerOnTrigger.valueOf(it) }.getOrNull() },
            done = sp.getBoolean(KEY_DONE, false),
            fires = sp.getString(KEY_FIRES, null).orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
        )
    } catch (e: Throwable) {
        PowerOnState()
    }

    /** Synchronous: a receiver's process may be gone right after. */
    fun save(s: PowerOnState) {
        sp.edit()
            .putLong(KEY_ARMED, s.armedAt)
            .putString(KEY_TRIGGER, s.trigger?.name)
            .putBoolean(KEY_DONE, s.done)
            .putString(KEY_FIRES, s.fires.joinToString(","))
            .commit()
    }

    /** One plain line for the Settings row, e.g. "Opened home (helper) at 21:04". */
    var lastOutcome: String?
        get() = sp.getString(KEY_LAST, null)
        set(v) { sp.edit().putString(KEY_LAST, v).apply() }

    companion object {
        const val NAME = "power_on"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ARMED = "armed_at"
        private const val KEY_TRIGGER = "trigger"
        private const val KEY_DONE = "done"
        private const val KEY_FIRES = "fires"
        private const val KEY_LAST = "last_outcome"
    }
}
