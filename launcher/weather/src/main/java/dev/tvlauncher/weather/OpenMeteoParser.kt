package dev.tvlauncher.weather

import org.json.JSONArray
import org.json.JSONObject

object OpenMeteoParser {
    private const val MAX_DAYS = 5
    private val DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    /**
     * Never throws (except OutOfMemoryError); returns null when the essential current data is
     * missing or implausible.
     */
    fun parseForecast(json: String, nowMs: Long = System.currentTimeMillis()): WeatherSnapshot? =
        JsonUtil.guarded { parse(JSONObject(json), nowMs) }

    private fun parse(root: JSONObject, nowMs: Long): WeatherSnapshot? {
        val cur = root.optJSONObject("current") ?: return null
        val temp = JsonUtil.temp(cur.opt("temperature_2m")) ?: return null
        val code = JsonUtil.num(cur, "weather_code")?.toInt() ?: return null
        val feels = JsonUtil.temp(cur.opt("apparent_temperature")) ?: temp
        val humidity = JsonUtil.humidity(cur.opt("relative_humidity_2m"))
        val wind = JsonUtil.wind(cur.opt("wind_speed_10m"))
        val isDay = (JsonUtil.num(cur, "is_day") ?: 1.0) != 0.0
        val offsetSec = (JsonUtil.num(root, "utc_offset_seconds") ?: 0.0).toLong()
        val d = root.optJSONObject("daily")
        val times = d?.optJSONArray("time")
        val daily = parseDaily(d, times, offsetSec)
        // Today's date: from current.time ("YYYY-MM-DDTHH:MM"), else the first daily date.
        val today = (cur.opt("time") as? String)?.take(10)?.takeIf { validDate(it) }
            ?: (times?.opt(0) as? String)
        val todayEntry = if (today == null || times == null) null else {
            // Only the entry whose own date is today counts; never shift to a neighbouring day.
            val idx = (0 until times.length()).firstOrNull { times.opt(it) == today }
            idx?.let { entryAt(d, times, it, offsetSec) }
        }
        return WeatherSnapshot(
            tempC = temp, feelsC = feels, code = code, isDay = isDay, humidity = humidity,
            windKmh = wind, highC = todayEntry?.highC, lowC = todayEntry?.lowC,
            updatedMs = nowMs, daily = daily,
        )
    }

    private fun parseDaily(d: JSONObject?, times: JSONArray?, offsetSec: Long): List<DailyForecast> {
        if (d == null || times == null) return emptyList()
        val out = ArrayList<DailyForecast>()
        for (i in 0 until times.length()) {
            if (out.size >= MAX_DAYS) break
            entryAt(d, times, i, offsetSec)?.let { out += it }
        }
        return out
    }

    private fun entryAt(d: JSONObject?, times: JSONArray, i: Int, offsetSec: Long): DailyForecast? {
        if (d == null) return null
        val dayMs = dayStartMs(times.opt(i) as? String, offsetSec) ?: return null
        val code = JsonUtil.finite(d.optJSONArray("weather_code")?.opt(i))?.toInt() ?: return null
        val hi = JsonUtil.temp(d.optJSONArray("temperature_2m_max")?.opt(i)) ?: return null
        val lo = JsonUtil.temp(d.optJSONArray("temperature_2m_min")?.opt(i)) ?: return null
        return DailyForecast(dayMs, code, hi, lo)
    }

    private fun validDate(s: String): Boolean = dayStartMs(s, 0) != null

    /**
     * "YYYY-MM-DD" in the location's zone -> epoch ms of that local midnight, using the
     * response's single utc_offset_seconds. Approximation: across a DST change inside the
     * 5-day window, later days can be off by the DST delta (1 h). Impossible dates are rejected.
     */
    internal fun dayStartMs(date: String?, offsetSec: Long): Long? {
        if (date == null) return null
        val m = DATE.matchEntire(date) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d < 1 || d > daysInMonth(y, mo)) return null
        return daysFromCivil(y, mo, d) * 86_400_000L - offsetSec * 1000L
    }

    private fun daysInMonth(y: Int, m: Int): Int = when (m) {
        2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
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
