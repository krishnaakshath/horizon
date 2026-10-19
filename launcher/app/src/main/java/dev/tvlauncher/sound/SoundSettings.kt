package dev.tvlauncher.sound

import android.content.Context
import org.json.JSONObject

/** Sound preferences; stored separately from LauncherSettings. */
data class SoundSettings(
    val enabled: Boolean = true,
    val themeId: String = "glass",
    val volume: Int = 60,
    val perEvent: Map<SoundEvent, Boolean> = SoundEvent.values().associateWith { true },
    val useCustomFolder: Boolean = false
) {
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
                val theme = j.optString("themeId", d.themeId)
                SoundSettings(
                    enabled = j.optBoolean("enabled", d.enabled),
                    themeId = if (theme.isBlank() || theme == "null") d.themeId else theme,
                    volume = j.optInt("volume", d.volume).coerceIn(0, 100),
                    perEvent = per,
                    useCustomFolder = j.optBoolean("useCustomFolder", d.useCustomFolder)
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
