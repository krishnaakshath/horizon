package dev.tvlauncher.splash

import android.content.Context

/** Stores [MotionStyleSettings] in SharedPreferences [STORE] (included in the configuration backup). */
class MotionStylePrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun load(): MotionStyleSettings = try {
        MotionStyleSettings.fromJson(sp.getString(KEY, null))
    } catch (e: RuntimeException) {
        MotionStyleSettings.DEFAULT
    }

    fun save(s: MotionStyleSettings) {
        sp.edit().putString(KEY, s.toJson()).apply()
    }

    fun update(f: (MotionStyleSettings) -> MotionStyleSettings): MotionStyleSettings = f(load()).also(::save)

    companion object {
        const val STORE = "motion_style"
        const val KEY = "json"
    }
}
