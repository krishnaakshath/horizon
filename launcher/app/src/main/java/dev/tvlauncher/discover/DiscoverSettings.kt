package dev.tvlauncher.discover

import android.content.Context
import dev.tvlauncher.nowplaying.NowPlayingSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Discover preferences. Tolerant JSON: anything malformed falls back per field, never throws.
 * [region] null = automatic ([RegionGuess]).
 */
data class DiscoverSettings(
    val region: String? = null,
    val showGetMoreAppsTile: Boolean = true,
    val hiddenCatalogIds: Set<String> = emptySet()
) {
    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("region", region ?: JSONObject.NULL)
        .put("showGetMoreAppsTile", showGetMoreAppsTile)
        .put("hidden", JSONArray(hiddenCatalogIds.sorted()))
        .toString()

    companion object {
        const val MAX_HIDDEN = 200
        private val ID = Regex("^[a-z0-9_-]{1,64}$")

        fun fromJson(s: String?): DiscoverSettings {
            if (s.isNullOrBlank()) return DiscoverSettings()
            val json = try { JSONObject(s) } catch (e: Exception) { return DiscoverSettings() }
            val d = DiscoverSettings()
            val region = if (json.isNull("region")) null else RegionGuess.normalize(json.optString("region"))
            val tile = json.opt("showGetMoreAppsTile") as? Boolean ?: d.showGetMoreAppsTile
            val hidden = LinkedHashSet<String>()
            val arr = json.optJSONArray("hidden")
            if (arr != null) for (i in 0 until arr.length()) {
                val v = arr.opt(i) as? String ?: continue
                if (ID.matches(v) && hidden.size < MAX_HIDDEN) hidden += v
            }
            return DiscoverSettings(region, tile, hidden)
        }
    }
}

/** SharedPreferences store for [DiscoverSettings] and [NowPlayingSettings] (two JSON strings). */
class DiscoverStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("discover_settings", Context.MODE_PRIVATE)

    fun load(): DiscoverSettings = try {
        DiscoverSettings.fromJson(prefs.getString(KEY_DISCOVER, null))
    } catch (e: Exception) {
        DiscoverSettings()
    }

    fun save(s: DiscoverSettings) {
        prefs.edit().putString(KEY_DISCOVER, s.toJson()).apply()
    }

    fun loadNowPlaying(): NowPlayingSettings = try {
        NowPlayingSettings.fromJson(prefs.getString(KEY_NOW_PLAYING, null))
    } catch (e: Exception) {
        NowPlayingSettings()
    }

    fun saveNowPlaying(s: NowPlayingSettings) {
        prefs.edit().putString(KEY_NOW_PLAYING, s.toJson()).apply()
    }

    companion object {
        const val KEY_DISCOVER = "discover_json"
        const val KEY_NOW_PLAYING = "now_playing_json"
    }
}
