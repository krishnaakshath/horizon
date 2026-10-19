package dev.tvlauncher.room

import android.content.Context

/** SharedPreferences "room_settings", key "json". Never throws: any failure reads as defaults. */
class RoomPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(): RoomSettings = try {
        RoomSettings.fromJson(prefs.getString(KEY, null))
    } catch (e: RuntimeException) {
        RoomSettings()
    }

    fun save(s: RoomSettings) {
        try {
            prefs.edit().putString(KEY, s.toJson()).apply()
        } catch (e: RuntimeException) {
            // Storage trouble: keep running with the in-memory value.
        }
    }

    companion object {
        const val NAME = "room_settings"
        const val KEY = "json"
    }
}
