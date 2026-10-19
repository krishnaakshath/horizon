package dev.tvlauncher.helper

import org.json.JSONArray
import org.json.JSONObject

data class HelperSettings(
    /** On by default: the service itself only runs once it is switched on. */
    val doubleBackEnabled: Boolean = true,
    val windowMs: Long = DoubleBackDetector.DEFAULT_MS,
    val shortcutKey: Int = 0,
    val exclusions: Set<String> = ExclusionList.DEFAULTS,
) {
    fun toJson(): String = JSONObject()
        .put("doubleBack", doubleBackEnabled)
        .put("windowMs", windowMs)
        .put("shortcutKey", shortcutKey)
        .put("exclusions", JSONArray(exclusions.sorted()))
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
            return HelperSettings(enabled, window, key, ex)
        }
    }
}
