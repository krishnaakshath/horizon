package dev.tvlauncher.splash

import org.json.JSONObject

/** What M02 shows while it starts (Settings > Appearance > Start-up and loading). */
enum class StartupStyle(val label: String) {
    SUNRISE("Sunrise"), MINIMAL("Minimal fade"), AURORA("Aurora sweep"), OFF("Off")
}

/** When the start-up animation plays. */
enum class StartupWhen(val label: String) {
    COLD_START("When M02 starts"), ALSO_WAKE("Also after standby")
}

/** The small "still loading" motif. */
enum class LoaderStyle(val label: String) {
    SUN_LINE("Sun on a line"), PULSE_RING("Pulse ring"), DOTS("Dots")
}

/** What happens between pressing OK on a tile and the app's first frame. */
enum class LaunchStyle(val label: String) {
    BRAND_EXPAND("Brand colour"), FADE("Fade"), ZOOM("Zoom from tile"), OFF("Off")
}

/** Overall pace. Every pace stays inside the animation level's caps ([MotionCaps]). */
enum class MotionPace(val label: String, val factor: Float) {
    FAST("Fast", 0.75f), NORMAL("Normal", 1f), CALM("Calm", 1.3f)
}

/**
 * Start-up and loading choices. Pure Kotlin + org.json (JVM-testable); [fromJson] never throws
 * and every field falls back to its default on its own.
 */
data class MotionStyleSettings(
    val startup: StartupStyle = StartupStyle.SUNRISE,
    val startupWhen: StartupWhen = StartupWhen.COLD_START,
    val loader: LoaderStyle = LoaderStyle.SUN_LINE,
    val launch: LaunchStyle = LaunchStyle.BRAND_EXPAND,
    val pace: MotionPace = MotionPace.NORMAL
) {
    fun toJson(): String = JSONObject().apply {
        put("version", VERSION)
        put("startup", startup.name)
        put("startupWhen", startupWhen.name)
        put("loader", loader.name)
        put("launch", launch.name)
        put("pace", pace.name)
    }.toString()

    companion object {
        const val VERSION = 1
        val DEFAULT = MotionStyleSettings()

        fun fromJson(s: String?): MotionStyleSettings {
            if (s.isNullOrBlank()) return DEFAULT
            val json = try { JSONObject(s) } catch (e: Exception) { return DEFAULT }
            return MotionStyleSettings(
                startup = enumOr(json, "startup", DEFAULT.startup),
                startupWhen = enumOr(json, "startupWhen", DEFAULT.startupWhen),
                loader = enumOr(json, "loader", DEFAULT.loader),
                launch = enumOr(json, "launch", DEFAULT.launch),
                pace = enumOr(json, "pace", DEFAULT.pace)
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(json: JSONObject, key: String, fallback: E): E {
            val raw = json.opt(key) as? String ?: return fallback
            return enumValues<E>().firstOrNull { it.name == raw } ?: fallback
        }
    }
}

/** Hard ceilings per animation level (ms). LITE never goes past these, whatever the pace. */
object MotionCaps {
    const val FULL_STARTUP_MS = 1100L
    const val LITE_STARTUP_MS = 700L
    const val FULL_LAUNCH_MS = 480L
    const val LITE_LAUNCH_MS = 350L

    fun startup(full: Boolean) = if (full) FULL_STARTUP_MS else LITE_STARTUP_MS
    fun launch(full: Boolean) = if (full) FULL_LAUNCH_MS else LITE_LAUNCH_MS
}
