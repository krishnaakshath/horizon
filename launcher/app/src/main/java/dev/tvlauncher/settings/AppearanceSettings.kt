package dev.tvlauncher.settings

import org.json.JSONObject

enum class AccentMode { AUTO_BRAND, FIXED }

enum class AnimationLevel { AUTO, FULL, LITE, OFF }

enum class TileSize { SMALL, MEDIUM, LARGE }

/**
 * How the launcher looks. Pure Kotlin + org.json so it is JVM-testable. [fromJson] never
 * throws: corrupt input yields defaults and every field falls back on its own when its value is
 * missing or unknown; unknown fields are ignored (same contract as [LauncherSettings]).
 */
data class AppearanceSettings(
    val accentMode: AccentMode = AccentMode.AUTO_BRAND,
    val fixedAccent: Int = DEFAULT_ACCENT,
    val animationLevel: AnimationLevel = AnimationLevel.AUTO,
    val clock24h: Boolean = true,
    val showDate: Boolean = true,
    val showStatusIcons: Boolean = true,
    val rowTileSize: TileSize = TileSize.MEDIUM
) {
    fun toJson(): String = JSONObject().apply {
        put("version", VERSION)
        put("accentMode", accentMode.name)
        put("fixedAccent", fixedAccent.toLong() and 0xFFFFFFFFL)
        put("animationLevel", animationLevel.name)
        put("clock24h", clock24h)
        put("showDate", showDate)
        put("showStatusIcons", showStatusIcons)
        put("rowTileSize", rowTileSize.name)
    }.toString()

    companion object {
        const val VERSION = 1
        const val DEFAULT_ACCENT = 0xFF4C8DFF.toInt()

        /** Eight preset accents for [AccentMode.FIXED], in display order. */
        val PRESETS: List<Int> = listOf(
            0xFF4C8DFF.toInt(), // blue
            0xFF8B6CFF.toInt(), // violet
            0xFFFF5C8A.toInt(), // rose
            0xFFFF6B3D.toInt(), // ember
            0xFFF5B83D.toInt(), // amber
            0xFF2FD07A.toInt(), // mint
            0xFF22C3D6.toInt(), // teal
            0xFFC9D1DE.toInt()  // silver
        )

        fun fromJson(s: String?): AppearanceSettings {
            val d = AppearanceSettings()
            if (s.isNullOrBlank()) return d
            val json = try {
                JSONObject(s)
            } catch (e: Exception) {
                return d
            }
            return try {
                AppearanceSettings(
                    accentMode = enumOr(json, "accentMode", d.accentMode),
                    fixedAccent = colorOr(json, "fixedAccent", d.fixedAccent),
                    animationLevel = enumOr(json, "animationLevel", d.animationLevel),
                    clock24h = boolOr(json, "clock24h", d.clock24h),
                    showDate = boolOr(json, "showDate", d.showDate),
                    showStatusIcons = boolOr(json, "showStatusIcons", d.showStatusIcons),
                    rowTileSize = enumOr(json, "rowTileSize", d.rowTileSize)
                )
            } catch (e: Exception) {
                d
            }
        }

        private inline fun <reified E : Enum<E>> enumOr(json: JSONObject, key: String, fallback: E): E {
            val raw = json.opt(key) as? String ?: return fallback
            return enumValues<E>().firstOrNull { it.name == raw } ?: fallback
        }

        private fun boolOr(json: JSONObject, key: String, fallback: Boolean): Boolean =
            json.opt(key) as? Boolean ?: fallback

        private fun colorOr(json: JSONObject, key: String, fallback: Int): Int {
            val n = json.opt(key) as? Number ?: return fallback
            val v = n.toLong()
            return if (v in 0L..0xFFFFFFFFL) v.toInt() else fallback
        }
    }
}
