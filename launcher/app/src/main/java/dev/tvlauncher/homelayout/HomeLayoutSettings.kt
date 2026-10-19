package dev.tvlauncher.homelayout

import org.json.JSONObject

/** How the home screen arranges its apps (Settings > Home layout > Layout style). */
enum class HomeStyle(val label: String) {
    /** One shelf under the hero: the classic home screen and the default. */
    SINGLE_ROW("Single row"),
    /** Two compact shelves: favorites on top, every other app below. */
    TWO_ROWS("Two rows"),
    /** Three or four rows of small tiles, paged up and down. */
    GRID("Grid"),
    /** The focused app's art large in the hero, a small row below. */
    BIG_HERO("Big hero"),
    /** The clock and one row of apps; no hero text. */
    MINIMAL("Minimal")
}

/** When tiles show their app name. */
enum class LabelMode(val label: String) { FOCUS("On focus"), ALWAYS("Always"), NEVER("Never") }

/**
 * The home layout choices. Pure Kotlin + org.json so it is JVM-testable. [fromJson] never throws:
 * a missing or corrupt value falls back per field, so an install without the store (every
 * install before this setting existed) keeps today's single row with names on focus.
 *
 * Tile size lives in AppearanceSettings.rowTileSize, the "Get more apps" tile in
 * DiscoverSettings and the Now Playing card in NowPlayingSettings; this page edits them in place.
 */
data class HomeLayoutSettings(
    val style: HomeStyle = HomeStyle.SINGLE_ROW,
    val labels: LabelMode = LabelMode.FOCUS,
    val heroSubtitle: Boolean = true,
    val quickStrip: Boolean = true
) {
    fun toJson(): String = JSONObject()
        .put("version", VERSION)
        .put("style", style.name)
        .put("labels", labels.name)
        .put("heroSubtitle", heroSubtitle)
        .put("quickStrip", quickStrip)
        .toString()

    companion object {
        const val VERSION = 1
        val DEFAULT = HomeLayoutSettings()

        fun fromJson(s: String?): HomeLayoutSettings {
            if (s.isNullOrBlank()) return DEFAULT
            val json = try { JSONObject(s) } catch (e: Exception) { return DEFAULT }
            return try {
                HomeLayoutSettings(
                    style = enumOr(json, "style", DEFAULT.style),
                    labels = enumOr(json, "labels", DEFAULT.labels),
                    heroSubtitle = json.opt("heroSubtitle") as? Boolean ?: DEFAULT.heroSubtitle,
                    quickStrip = json.opt("quickStrip") as? Boolean ?: DEFAULT.quickStrip
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }

        private inline fun <reified E : Enum<E>> enumOr(json: JSONObject, key: String, fallback: E): E {
            val raw = json.opt(key) as? String ?: return fallback
            return enumValues<E>().firstOrNull { it.name == raw } ?: fallback
        }
    }
}
