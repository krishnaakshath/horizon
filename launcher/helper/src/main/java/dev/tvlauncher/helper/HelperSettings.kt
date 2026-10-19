package dev.tvlauncher.helper

import org.json.JSONArray
import org.json.JSONObject

data class HelperSettings(
    /** On by default: the service itself only runs once it is switched on. */
    val doubleBackEnabled: Boolean = true,
    val windowMs: Long = DoubleBackDetector.DEFAULT_MS,
    val shortcutKey: Int = 0,
    val exclusions: Set<String> = ExclusionList.DEFAULTS,
    /** Spotlight's extra key as set in the launcher (Settings > Home > Spotlight); 0 = none. */
    val launcherKey: Int = 0,
    /** The launcher's extra key opens Spotlight on a double-press only. */
    val launcherDouble: Boolean = false,
    val launcherWindowMs: Long = SpotlightSync.DEFAULT_WINDOW_MS,
) {
    /** The key the service acts on: the launcher's when it set one, else the one set on this app's screen. */
    val effectiveKey: Int get() = if (launcherKey != 0) launcherKey else shortcutKey

    /** Double-press applies only to the launcher's key. */
    val effectiveDouble: Boolean get() = launcherKey != 0 && launcherDouble

    fun toJson(): String = JSONObject()
        .put("doubleBack", doubleBackEnabled)
        .put("windowMs", windowMs)
        .put("shortcutKey", shortcutKey)
        .put("exclusions", JSONArray(exclusions.sorted()))
        .put("launcherKey", launcherKey)
        .put("launcherDouble", launcherDouble)
        .put("launcherWindowMs", launcherWindowMs)
        .toString()

    companion object {
        fun fromJson(json: String?): HelperSettings {
            val o = try {
                JSONObject(json ?: return HelperSettings())
            } catch (e: Exception) {
                return HelperSettings()
            }
            val d = HelperSettings()
            val enabled = o.opt("doubleBack") as? Boolean ?: d.doubleBackEnabled
            val window = (o.opt("windowMs") as? Number)?.toLong()
                ?.let { DoubleBackDetector.clampWindow(it) } ?: d.windowMs
            val key = (o.opt("shortcutKey") as? Number)?.toInt()
                ?.takeIf { ShortcutKeys.isAllowed(it) } ?: 0
            val arr = o.opt("exclusions") as? JSONArray
            val ex = if (arr == null) d.exclusions else
                ExclusionList.parse((0 until arr.length()).joinToString("\n") { arr.optString(it, "") })
            val lKey = (o.opt("launcherKey") as? Number)?.toInt()?.takeIf { ShortcutKeys.isAllowed(it) } ?: 0
            val lDouble = lKey != 0 && (o.opt("launcherDouble") as? Boolean ?: false)
            val lWindow = SpotlightSync.clampWindow((o.opt("launcherWindowMs") as? Number)?.toLong() ?: SpotlightSync.DEFAULT_WINDOW_MS)
            return HelperSettings(enabled, window, key, ex, lKey, lDouble, lWindow)
        }
    }
}
