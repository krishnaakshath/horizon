package dev.tvlauncher.settings

import android.content.Context

/** Persists [AppearanceSettings] as one JSON string (SharedPreferences "appearance_settings"). */
class AppearancePrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): AppearanceSettings = AppearanceSettings.fromJson(sp.getString(KEY, null))

    fun save(settings: AppearanceSettings) {
        sp.edit().putString(KEY, settings.toJson()).apply()
    }

    fun update(change: (AppearanceSettings) -> AppearanceSettings): AppearanceSettings =
        change(load()).also { save(it) }

    companion object {
        const val FILE = "appearance_settings"
        const val KEY = "json"
    }
}
