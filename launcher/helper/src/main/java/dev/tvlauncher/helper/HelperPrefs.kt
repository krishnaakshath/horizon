package dev.tvlauncher.helper

import android.content.Context
import android.content.SharedPreferences

class HelperPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): HelperSettings = HelperSettings.fromJson(sp.getString(KEY, null))

    fun save(settings: HelperSettings) {
        sp.edit().putString(KEY, settings.toJson()).apply()
    }

    fun update(change: (HelperSettings) -> HelperSettings): HelperSettings {
        val next = change(load())
        save(next)
        return next
    }

    companion object {
        const val NAME = "helper_prefs"
        private const val KEY = "settings_json"
    }
}
