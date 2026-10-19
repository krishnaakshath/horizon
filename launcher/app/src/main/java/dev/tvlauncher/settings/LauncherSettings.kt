package dev.tvlauncher.settings

import org.json.JSONObject
import org.json.JSONArray

data class LauncherSettings(
    val version: Int = 1,
    val favorites: List<String> = emptyList(),
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val theme: String = "dark"
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("version", version)
        json.put("favorites", JSONArray(favorites))
        json.put("order", JSONArray(order))
        json.put("hidden", JSONArray(hidden.toList()))
        json.put("theme", theme)
        return json.toString()
    }

    companion object {
        fun fromJson(s: String): LauncherSettings {
            return try {
                if (s.isBlank()) {
                    return LauncherSettings()
                }
                val json = JSONObject(s)
                val version = json.optInt("version", 1)
                val favoritesArray = json.optJSONArray("favorites") ?: JSONArray()
                val favorites = (0 until favoritesArray.length()).map { i ->
                    favoritesArray.getString(i)
                }
                val orderArray = json.optJSONArray("order") ?: JSONArray()
                val order = (0 until orderArray.length()).map { i ->
                    orderArray.getString(i)
                }
                val hiddenArray = json.optJSONArray("hidden") ?: JSONArray()
                val hidden = (0 until hiddenArray.length()).map { i ->
                    hiddenArray.getString(i)
                }.toSet()
                val theme = json.optString("theme", "dark")

                LauncherSettings(
                    version = version,
                    favorites = favorites,
                    order = order,
                    hidden = hidden,
                    theme = theme
                )
            } catch (e: Exception) {
                // Malformed input or any other error returns defaults
                LauncherSettings()
            }
        }
    }
}
