package dev.tvlauncher.sound

import android.content.Context
import org.json.JSONObject

/** Sound preferences; stored separately from LauncherSettings. */
data class SoundSettings(
    val enabled: Boolean = true,
    val themeId: String = "glass",
    val volume: Int = 60,
    val perEvent: Map<SoundEvent, Boolean> = SoundEvent.values().associateWith { true },
    val useCustomFolder: Boolean = false,
    /** Theme for MOVE/SELECT/BACK/MENU_OPEN. Defaults to [themeId] for backwards compatibility. */
    val navThemeId: String = themeId,
    /** Theme for LAUNCH/ERROR. Defaults to [themeId]. */
    val launchThemeId: String = themeId
) {
    fun themeIdFor(e: SoundEvent): String = when (e) {
        SoundEvent.LAUNCH, SoundEvent.ERROR -> launchThemeId
        else -> navThemeId
    }

    fun isEventOn(e: SoundEvent) = perEvent[e] ?: true

    fun toJson(): String {
        val ev = JSONObject()
        SoundEvent.values().forEach { ev.put(it.name, isEventOn(it)) }
        return JSONObject()
            .put("version", 1)
            .put("enabled", enabled)
            .put("themeId", themeId)
            .put("volume", volume)
            .put("perEvent", ev)
            .put("useCustomFolder", useCustomFolder)
            .put("navThemeId", navThemeId)
            .put("launchThemeId", launchThemeId)
            .toString()
    }

    companion object {
        fun fromJson(s: String): SoundSettings {
            return try {
                if (s.isBlank()) return SoundSettings()
                val j = JSONObject(s)
                val d = SoundSettings()
                val evObj = j.optJSONObject("perEvent")
                val per = SoundEvent.values().associateWith { e ->
                    evObj?.optBoolean(e.name, true) ?: true
                }
                fun id(key: String, fallback: String): String {
                    val v = j.optString(key, fallback)
                    return if (v.isBlank() || v == "null") fallback else v
                }
                val theme = id("themeId", d.themeId)
                SoundSettings(
                    enabled = j.optBoolean("enabled", d.enabled),
                    themeId = theme,
                    volume = j.optInt("volume", d.volume).coerceIn(0, 100),
                    perEvent = per,
                    useCustomFolder = j.optBoolean("useCustomFolder", d.useCustomFolder),
                    navThemeId = id("navThemeId", theme),
                    launchThemeId = id("launchThemeId", theme)
                )
            } catch (e: Exception) {
                SoundSettings()
            }
        }
    }
}

/** SharedPreferences "sound_settings", key "json". */
class SoundPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sound_settings", Context.MODE_PRIVATE)

    fun load(): SoundSettings = SoundSettings.fromJson(prefs.getString("json", "") ?: "")

    fun save(s: SoundSettings) {
        prefs.edit().putString("json", s.toJson()).apply()
    }
}
