package dev.tvlauncher.ambient

import org.json.JSONObject
import java.io.File

/** Where the wallpaper comes from. Built-ins are procedural (no image assets). */
sealed class WallpaperSource {
    data class BuiltIn(val id: String) : WallpaperSource()
    data class UserImage(val file: File) : WallpaperSource()
    data class Slideshow(val folder: File, val intervalSec: Int) : WallpaperSource()

    companion object {
        const val DEFAULT_ID = "aurora"
        val BUILT_IN_IDS = listOf("aurora", "dusk", "ocean", "graphite", "nebula", "mono")
        val DEFAULT: WallpaperSource = BuiltIn(DEFAULT_ID)
    }
}

/** Canonical-path containment check for wallpaper files read back from stored JSON. */
object PathGuard {
    /** True when [candidate] resolves (symlinks, "..") strictly inside [base] (or equals it when [allowSame]). */
    fun isInside(base: File?, candidate: File, allowSame: Boolean = false): Boolean {
        if (base == null) return false
        return try {
            val b = base.canonicalFile.path.trimEnd(File.separatorChar)
            val c = candidate.canonicalFile.path.trimEnd(File.separatorChar)
            (allowSame && c == b) || c.startsWith(b + File.separator)
        } catch (e: Exception) {
            false
        }
    }
}

enum class ClockPosition { BOTTOM_LEFT, CENTER, TOP_LEFT }

/**
 * Wallpaper + ambient preferences. Tolerant JSON like LauncherSettings: anything malformed or
 * out of range falls back per field, never throws. Blur is deliberately not supported.
 */
data class WallpaperSettings(
    val source: WallpaperSource = WallpaperSource.DEFAULT,
    val dimPercent: Int = 0,
    val clockPosition: ClockPosition = ClockPosition.BOTTOM_LEFT,
    val shuffle: Boolean = false,
    val clock24h: Boolean = true,
    val nightDim: Boolean = true,
    /** Notification chips are opt-in; the listener permission is a separate system grant. */
    val notificationsEnabled: Boolean = false,
    val privacyAppNameOnly: Boolean = true,
    /** Owner opted in to showing message text (public notifications only). Default off. */
    val showNotificationText: Boolean = false,
    /** Minutes of no key input on Home before ambient starts. */
    val idleMinutes: Int = 10
) {
    fun normalized(): WallpaperSettings = copy(
        dimPercent = dimPercent.coerceIn(0, MAX_DIM),
        idleMinutes = idleMinutes.coerceIn(MIN_IDLE, MAX_IDLE),
        source = when (source) {
            is WallpaperSource.Slideshow -> source.copy(intervalSec = WallpaperPlan.clampInterval(source.intervalSec))
            is WallpaperSource.BuiltIn -> if (source.id in WallpaperSource.BUILT_IN_IDS) source else WallpaperSource.DEFAULT
            else -> source
        }
    )

    /** Drops any user path that does not resolve inside [wallpaperDir] (falls back to the default built-in). */
    fun sanitizedFor(wallpaperDir: File?): WallpaperSettings = when (val s = source) {
        is WallpaperSource.BuiltIn -> this
        is WallpaperSource.UserImage -> if (PathGuard.isInside(wallpaperDir, s.file)) this else copy(source = WallpaperSource.DEFAULT)
        is WallpaperSource.Slideshow -> if (PathGuard.isInside(wallpaperDir, s.folder, allowSame = true)) this else copy(source = WallpaperSource.DEFAULT)
    }

    fun toJson(): String {
        val s = normalized()
        val src = JSONObject()
        when (val x = s.source) {
            is WallpaperSource.BuiltIn -> { src.put("type", "builtin"); src.put("id", x.id) }
            is WallpaperSource.UserImage -> { src.put("type", "image"); src.put("file", x.file.path) }
            is WallpaperSource.Slideshow -> {
                src.put("type", "slideshow"); src.put("folder", x.folder.path); src.put("interval", x.intervalSec)
            }
        }
        return JSONObject()
            .put("version", 1)
            .put("source", src)
            .put("dim", s.dimPercent)
            .put("clock", s.clockPosition.name)
            .put("shuffle", s.shuffle)
            .put("clock24h", s.clock24h)
            .put("nightDim", s.nightDim)
            .put("notifications", s.notificationsEnabled)
            .put("privacyAppOnly", s.privacyAppNameOnly)
            .put("showText", s.showNotificationText)
            .put("idleMinutes", s.idleMinutes)
            .toString()
    }

    companion object {
        const val MAX_DIM = 60
        const val MIN_IDLE = 1
        const val MAX_IDLE = 120

        fun fromJson(s: String?): WallpaperSettings {
            if (s.isNullOrBlank()) return WallpaperSettings()
            val json = try { JSONObject(s) } catch (e: Exception) { return WallpaperSettings() }
            val d = WallpaperSettings()
            return WallpaperSettings(
                source = parseSource(json.optJSONObject("source")),
                dimPercent = json.optInt("dim", d.dimPercent),
                clockPosition = ClockPosition.values().firstOrNull { it.name == json.optString("clock") } ?: d.clockPosition,
                shuffle = json.optBoolean("shuffle", d.shuffle),
                clock24h = json.optBoolean("clock24h", d.clock24h),
                nightDim = json.optBoolean("nightDim", d.nightDim),
                notificationsEnabled = json.optBoolean("notifications", d.notificationsEnabled),
                privacyAppNameOnly = json.optBoolean("privacyAppOnly", d.privacyAppNameOnly),
                showNotificationText = json.optBoolean("showText", d.showNotificationText),
                idleMinutes = json.optInt("idleMinutes", d.idleMinutes)
            ).normalized()
        }

        private fun parseSource(o: JSONObject?): WallpaperSource {
            if (o == null) return WallpaperSource.DEFAULT
            return when (o.optString("type")) {
                "builtin" -> WallpaperSource.BuiltIn(o.optString("id", WallpaperSource.DEFAULT_ID))
                "image" -> o.optString("file").takeIf { it.isNotBlank() }
                    ?.let { WallpaperSource.UserImage(File(it)) } ?: WallpaperSource.DEFAULT
                "slideshow" -> o.optString("folder").takeIf { it.isNotBlank() }
                    ?.let { WallpaperSource.Slideshow(File(it), o.optInt("interval", WallpaperPlan.DEFAULT_INTERVAL_SEC)) }
                    ?: WallpaperSource.DEFAULT
                else -> WallpaperSource.DEFAULT
            }
        }
    }
}
