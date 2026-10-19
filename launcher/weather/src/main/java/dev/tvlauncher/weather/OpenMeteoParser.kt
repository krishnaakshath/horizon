package dev.tvlauncher.weather

import org.json.JSONArray
import org.json.JSONObject

object OpenMeteoParser {
    private const val MAX_DAYS = 5

    /** Never throws; returns null when the essential current data is missing or invalid. */
    fun parseForecast(json: String, nowMs: Long = System.currentTimeMillis()): WeatherSnapshot? = try {
        parse(JSONObject(json), nowMs)
    } catch (e: Exception) {
        null
    }

    private fun parse(root: JSONObject, nowMs: Long): WeatherSnapshot? {
        val cur = root.optJSONObject("current") ?: return null
        val temp = JsonUtil.num(cur, "temperature_2m") ?: return null
        val code = JsonUtil.num(cur, "weather_code")?.toInt() ?: return null
        val feels = JsonUtil.num(cur, "apparent_temperature") ?: temp
        val humidity = (JsonUtil.num(cur, "relative_humidity_2m") ?: 0.0).toInt().coerceIn(0, 100)
        val wind = (JsonUtil.num(cur, "wind_speed_10m") ?: 0.0).coerceAtLeast(0.0)
        val isDay = (JsonUtil.num(cur, "is_day") ?: 1.0) != 0.0
        val offsetSec = (JsonUtil.num(root, "utc_offset_seconds") ?: 0.0).toLong()
        val daily = parseDaily(root.optJSONObject("daily"), offsetSec)
        return WeatherSnapshot(
            tempC = temp, feelsC = feels, code = code, isDay = isDay, humidity = humidity,
            windKmh = wind,
            highC = daily.firstOrNull()?.highC ?: temp,
            lowC = daily.firstOrNull()?.lowC ?: temp,
            updatedMs = nowMs, daily = daily,
        )
    }

    private fun parseDaily(d: JSONObject?, offsetSec: Long): List<DailyForecast> {
        if (d == null) return emptyList()
        val times = d.optJSONArray("time") ?: return emptyList()
        val codes = d.optJSONArray("weather_code")
        val max = d.optJSONArray("temperature_2m_max")
        val min = d.optJSONArray("temperature_2m_min")
        val out = ArrayList<DailyForecast>()
        for (i in 0 until times.length()) {
            if (out.size >= MAX_DAYS) break
            val dayMs = dayStartMs(times.opt(i) as? String, offsetSec) ?: continue
            val code = numAt(codes, i)?.toInt() ?: continue
            val hi = numAt(max, i) ?: continue
            val lo = numAt(min, i) ?: continue
            out += DailyForecast(dayMs, code, hi, lo)
        }
        return out
    }

    private fun numAt(a: JSONArray?, i: Int): Double? {
        val v = a?.opt(i)
        if (v !is Number) return null
        val d = v.toDouble()
        return if (d.isFinite()) d else null
    }

    /** "YYYY-MM-DD" in the location's zone -> epoch ms of that local midnight. */
    internal fun dayStartMs(date: String?, offsetSec: Long): Long? {
        if (date == null) return null
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(date) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        return daysFromCivil(y, mo, d) * 86_400_000L - offsetSec * 1000L
    }

    // Howard Hinnant's days-from-civil (no java.time on API < 26).
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = (if (m <= 2) y0 - 1 else y0).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
