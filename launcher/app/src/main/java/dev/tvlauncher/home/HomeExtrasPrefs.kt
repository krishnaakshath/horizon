package dev.tvlauncher.home

import android.content.Context

/**
 * Wave-2 home options set from Settings (WaveTwoSections): the weather chip in the top bar
 * (default ON) and the ambient wallpaper under the home backdrop (default OFF).
 */
class HomeExtrasPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var weatherInTopBar: Boolean
        get() = sp.getBoolean(KEY_WEATHER, true)
        set(v) { sp.edit().putBoolean(KEY_WEATHER, v).apply() }

    var wallpaperOnHome: Boolean
        get() = sp.getBoolean(KEY_WALLPAPER, false)
        set(v) { sp.edit().putBoolean(KEY_WALLPAPER, v).apply() }

    companion object {
        const val FILE = "home_extras"
        const val KEY_WEATHER = "weather_in_top_bar"
        const val KEY_WALLPAPER = "wallpaper_on_home"
    }
}
