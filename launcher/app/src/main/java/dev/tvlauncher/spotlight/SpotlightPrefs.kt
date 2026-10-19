package dev.tvlauncher.spotlight

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Spotlight's saved state in its own preferences file [FILE]: the settings ([SpotlightConfig]
 * JSON under [KEY_CONFIG]), recently opened apps and the helper's last report. Saving the
 * settings also pushes the extra key to the helper ([HelperBridge]).
 */
class SpotlightPrefs(context: Context) {
    private val app = context.applicationContext
    val sp: SharedPreferences = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): SpotlightConfig = SpotlightConfig.fromJson(sp.getString(KEY_CONFIG, null))

    fun save(c: SpotlightConfig) {
        sp.edit().putString(KEY_CONFIG, c.toJson()).apply()
        HelperBridge.push(app, c)
    }

    fun update(change: (SpotlightConfig) -> SpotlightConfig): SpotlightConfig = change(load()).also(::save)

    fun reset(): SpotlightConfig = SpotlightConfig.DEFAULT.also(::save)

    fun recent(): List<String> = try {
        val a = JSONArray(sp.getString(KEY_RECENT, "[]"))
        (0 until a.length()).map { a.optString(it, "") }.filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }

    fun recordLaunch(pkg: String) {
        if (pkg.isEmpty()) return
        sp.edit().putString(KEY_RECENT, JSONArray(SpotlightResults.record(recent(), pkg)).toString()).apply()
    }

    fun report(): HelperLink.Report? {
        if (!sp.contains(KEY_H_SERVICE)) return null
        return HelperLink.Report(
            serviceOn = sp.getBoolean(KEY_H_SERVICE, false),
            syncedKey = sp.getInt(KEY_H_SYNCED, 0),
            syncedDouble = sp.getBoolean(KEY_H_DOUBLE, false),
            effectiveKey = sp.getInt(KEY_H_EFFECTIVE, 0),
            hello = false
        )
    }

    fun saveReport(r: HelperLink.Report) {
        sp.edit()
            .putBoolean(KEY_H_SERVICE, r.serviceOn)
            .putInt(KEY_H_SYNCED, r.syncedKey)
            .putBoolean(KEY_H_DOUBLE, r.syncedDouble)
            .putInt(KEY_H_EFFECTIVE, r.effectiveKey)
            .apply()
    }

    companion object {
        const val FILE = "spotlight"
        const val KEY_CONFIG = "config_json"
        const val KEY_RECENT = "recent_json"
        const val KEY_H_SERVICE = "helper_service_on"
        const val KEY_H_SYNCED = "helper_synced_key"
        const val KEY_H_DOUBLE = "helper_synced_double"
        const val KEY_H_EFFECTIVE = "helper_effective_key"
        val HELPER_KEYS = setOf(KEY_H_SERVICE, KEY_H_SYNCED, KEY_H_DOUBLE, KEY_H_EFFECTIVE)
    }
}
