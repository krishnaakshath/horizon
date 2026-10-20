package dev.tvlauncher.homesections

import android.content.Context

/**
 * Persists [HomeSections] as one versioned JSON string in SharedPreferences [FILE] (per profile via
 * profiles.ProfileStores, part of the whole-config backup). A missing store reads as Classic.
 */
class HomeSectionsPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): HomeSections = try {
        HomeSections.fromJson(sp.getString(KEY, null))
    } catch (e: RuntimeException) {
        HomeSections.CLASSIC
    }

    fun save(s: HomeSections) {
        sp.edit().putString(KEY, s.normalized().toJson()).apply()
    }

    fun update(change: (HomeSections) -> HomeSections): HomeSections =
        change(load()).normalized().also { save(it) }

    companion object {
        const val FILE = "home_sections"
        const val KEY = "json"
    }
}
