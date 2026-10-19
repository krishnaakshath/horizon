package dev.tvlauncher.weather

import org.json.JSONObject

internal object JsonUtil {
    const val TEMP_MIN = -90.0
    const val TEMP_MAX = 60.0
    const val WIND_MAX = 500.0

    /** Runs [block]; any Throwable (e.g. StackOverflowError on hostile nesting) yields null. OOM propagates. */
    inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: OutOfMemoryError) {
        throw e
    } catch (t: Throwable) {
        null
    }

    /** A finite JSON number, or null (missing, null, string, NaN/Infinity). */
    fun num(o: JSONObject, key: String): Double? = finite(o.opt(key))

    fun finite(v: Any?): Double? {
        if (v !is Number) return null
        val d = v.toDouble()
        return if (d.isFinite()) d else null
    }

    /** A finite temperature within the plausible range, or null. */
    fun temp(v: Any?): Double? = finite(v)?.takeIf { it in TEMP_MIN..TEMP_MAX }

    fun str(o: JSONObject, key: String): String? =
        (o.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /** Plausible wind speed or 0.0 when missing/implausible. */
    fun wind(v: Any?): Double = finite(v)?.takeIf { it in 0.0..WIND_MAX } ?: 0.0

    fun humidity(v: Any?): Int = (finite(v) ?: 0.0).coerceIn(0.0, 100.0).toInt()
}
