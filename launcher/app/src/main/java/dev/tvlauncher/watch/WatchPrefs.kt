package dev.tvlauncher.watch

import android.content.Context
import android.content.SharedPreferences

/** Continue watching settings in their own preferences file; nothing here leaves the TV. */
class WatchPrefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): WatchSettings = try {
        WatchSettings.fromJson(sp.getString(KEY, null))
    } catch (e: RuntimeException) {
        WatchSettings.DEFAULT
    }

    fun save(s: WatchSettings) {
        sp.edit().putString(KEY, s.toJson()).apply()
    }

    fun update(change: (WatchSettings) -> WatchSettings): WatchSettings = change(load()).also(::save)

    companion object {
        const val FILE = "watch"
        const val KEY = "settings_json"
    }
}
