package dev.tvlauncher.homelayout

import android.content.Context

/** Persists [HomeLayoutSettings] as one JSON string (SharedPreferences [FILE], part of the whole-config backup). */
class HomeLayoutPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): HomeLayoutSettings = try {
        HomeLayoutSettings.fromJson(sp.getString(KEY, null))
    } catch (e: RuntimeException) {
        HomeLayoutSettings.DEFAULT
    }

    fun save(s: HomeLayoutSettings) {
        sp.edit().putString(KEY, s.toJson()).apply()
    }

    fun update(change: (HomeLayoutSettings) -> HomeLayoutSettings): HomeLayoutSettings =
        change(load()).also { save(it) }

    companion object {
        const val FILE = "home_layout"
        const val KEY = "json"
    }
}
