package dev.tvlauncher.settings

import org.json.JSONObject

enum class AccentMode { AUTO_BRAND, FIXED }

enum class AnimationLevel { AUTO, FULL, LITE, OFF }

enum class TileSize { SMALL, MEDIUM, LARGE }

/**
 * How glass surfaces look (Settings > Appearance > Glass style). LIQUID: Apple-style liquid glass
 * (lensing rim, specular highlights, springy shapes); FROSTED: blurred translucent panels; CLEAR:
 * light translucent glass with a scrim behind text; SOLID: the old near-opaque cards.
 */
enum class GlassStyle(val label: String) {
    LIQUID("Liquid Glass"), FROSTED("Frosted"), CLEAR("Clear"), SOLID("Solid");

    companion object {
        /** Liquid Glass on capable TVs; Clear (with its text scrim) on LITE ones. */
        fun default(full: Boolean): GlassStyle = if (full) LIQUID else CLEAR

        /** [chosen] (null = never picked) resolved for this TV. */
        fun resolve(chosen: GlassStyle?, full: Boolean): GlassStyle = chosen ?: default(full)
    }
}

/** AUTO follows the TV's own 12/24-hour setting. */
enum class ClockFormat { AUTO, H24, H12 }

/**
 * How the launcher looks. Pure Kotlin + org.json so it is JVM-testable. [fromJson] never
 * throws: corrupt input yields defaults and every field falls back on its own when its value is
 * missing or unknown; unknown fields are ignored (same contract as [LauncherSettings]).
 */
data class AppearanceSettings(
    val accentMode: AccentMode = AccentMode.AUTO_BRAND,
    val fixedAccent: Int = DEFAULT_ACCENT,
    val animationLevel: AnimationLevel = AnimationLevel.AUTO,
    val clockFormat: ClockFormat = ClockFormat.AUTO,
    val showDate: Boolean = true,
    val showStatusIcons: Boolean = true,
    val rowTileSize: TileSize = TileSize.MEDIUM,
    /** Null until the user picks one: [GlassStyle.default] for the TV then. */
    val glassStyle: GlassStyle? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("version", VERSION)
        put("accentMode", accentMode.name)
        put("fixedAccent", fixedAccent.toLong() and 0xFFFFFFFFL)
        put("animationLevel", animationLevel.name)
        put("clockFormat", clockFormat.name)
        put("showDate", showDate)
        put("showStatusIcons", showStatusIcons)
        put("rowTileSize", rowTileSize.name)
        glassStyle?.let { put("glassStyle", it.name) }
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
                    clockFormat = clockFormatOf(json),
                    showDate = boolOr(json, "showDate", d.showDate),
                    showStatusIcons = boolOr(json, "showStatusIcons", d.showStatusIcons),
                    rowTileSize = enumOr(json, "rowTileSize", d.rowTileSize),
                    glassStyle = (json.opt("glassStyle") as? String)?.let { raw -> GlassStyle.values().firstOrNull { it.name == raw } }
                )
            } catch (e: Exception) {
                d
            }
        }

        private inline fun <reified E : Enum<E>> enumOr(json: JSONObject, key: String, fallback: E): E {
            val raw = json.opt(key) as? String ?: return fallback
            return enumValues<E>().firstOrNull { it.name == raw } ?: fallback
        }

        /** "clockFormat" wins; else the legacy "clock24h" boolean (true = H24, false = H12); else AUTO. */
        private fun clockFormatOf(json: JSONObject): ClockFormat {
            if (json.has("clockFormat")) return enumOr(json, "clockFormat", ClockFormat.AUTO)
            return when (json.opt("clock24h") as? Boolean) {
                true -> ClockFormat.H24
                false -> ClockFormat.H12
                null -> ClockFormat.AUTO
            }
        }

        private fun boolOr(json: JSONObject, key: String, fallback: Boolean): Boolean =
            json.opt(key) as? Boolean ?: fallback

        private fun colorOr(json: JSONObject, key: String, fallback: Int): Int {
            val n = json.opt(key) as? Number ?: return fallback
            val v = n.toLong()
            // Always opaque: a corrupt value like 255 must not make highlights invisible.
            return if (v in 0L..0xFFFFFFFFL) v.toInt() or 0xFF000000.toInt() else fallback
        }
    }
}
