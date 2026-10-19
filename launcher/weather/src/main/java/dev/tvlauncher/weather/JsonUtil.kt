package dev.tvlauncher.weather

import org.json.JSONObject

internal object JsonUtil {
    /** A finite JSON number, or null (missing, null, string, NaN/Infinity). */
    fun num(o: JSONObject, key: String): Double? {
        val v = o.opt(key)
        if (v !is Number) return null
        val d = v.toDouble()
        return if (d.isFinite()) d else null
    }

    fun str(o: JSONObject, key: String): String? =
        (o.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
