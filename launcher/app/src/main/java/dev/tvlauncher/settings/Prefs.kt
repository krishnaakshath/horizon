package dev.tvlauncher.settings

import android.content.Context

class Prefs(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    fun load(): LauncherSettings {
        val json = sharedPreferences.getString("settings_json", null)
        return if (json != null) {
            LauncherSettings.fromJson(json)
        } else {
            LauncherSettings()
        }
    }

    fun save(settings: LauncherSettings) {
        val json = settings.toJson()
        sharedPreferences.edit().putString("settings_json", json).apply()
    }
}
