package dev.tvlauncher.ambient

import kotlin.math.roundToLong

/** Binding contract with the companion weather app (the launcher itself has no INTERNET). */
object WeatherContract {
    const val PACKAGE = "dev.tvlauncher.weather"
    const val AUTHORITY = "dev.tvlauncher.weather"
    const val CURRENT_URI = "content://$AUTHORITY/current"
    const val DAILY_URI = "content://$AUTHORITY/daily"
    const val ACTION_SETTINGS = "dev.tvlauncher.weather.action.SETTINGS"
    const val ACTION_REFRESH = "dev.tvlauncher.weather.action.REFRESH"
    const val MAX_DAYS = 5
}

data class WeatherCurrent(
    val location: String,
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val condition: String,
    val isDay: Boolean,
    val highC: Double,
    val lowC: Double,
    val humidity: Int,
    val windKmh: Double,
    val updatedMs: Long,
    /** Display preference only; all values are Celsius. */
    val unit: String
)

data class WeatherDay(val dayMs: Long, val code: Int, val condition: String, val highC: Double, val lowC: Double)

data class WeatherSnapshot(val current: WeatherCurrent, val daily: List<WeatherDay>)

/** Rows as column-name -> value maps, so parsing is testable without a Cursor. */
interface WeatherSource {
    /** null when the provider is missing. */
    fun queryCurrent(): List<Map<String, Any?>>?
    fun queryDaily(): List<Map<String, Any?>>?
}

enum class WxIcon(val key: String) {
    CLEAR_DAY("ic_wx_clear_day"),
    CLEAR_NIGHT("ic_wx_clear_night"),
    PARTLY_DAY("ic_wx_partly_day"),
    PARTLY_NIGHT("ic_wx_partly_night"),
    CLOUDY("ic_wx_cloudy"),
    FOG("ic_wx_fog"),
    DRIZZLE("ic_wx_drizzle"),
    RAIN("ic_wx_rain"),
    SNOW("ic_wx_snow"),
    STORM("ic_wx_storm")
}

object WeatherFormat {
    /** "21°" rounded half-up in the display unit; never "-0°". */
    fun temp(tempC: Double, unit: String?): String {
        if (tempC.isNaN() || tempC.isInfinite()) return "–"
        val v = if (unit.equals("F", ignoreCase = true)) tempC * 9.0 / 5.0 + 32.0 else tempC
        val r = v.roundToLong()
        return "${if (r == 0L) 0L else r}°"
    }

    fun highLow(highC: Double, lowC: Double, unit: String?): String = "H ${temp(highC, unit)}  L ${temp(lowC, unit)}"

    fun iconFor(code: Int, isDay: Boolean): WxIcon = when (code) {
        0 -> if (isDay) WxIcon.CLEAR_DAY else WxIcon.CLEAR_NIGHT
        1, 2 -> if (isDay) WxIcon.PARTLY_DAY else WxIcon.PARTLY_NIGHT
        3 -> WxIcon.CLOUDY
        45, 48 -> WxIcon.FOG
        in 51..57 -> WxIcon.DRIZZLE
        in 61..67, in 80..82 -> WxIcon.RAIN
        in 71..77, 85, 86 -> WxIcon.SNOW
        in 95..99 -> WxIcon.STORM
        else -> WxIcon.CLOUDY
    }

    /** Short English text for a WMO code, used when the provider sends no condition. */
    fun conditionFor(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}

object WeatherFreshness {
    const val STALE_MS = 3 * 60 * 60_000L
    const val UNAVAILABLE = "Weather unavailable"

    fun isUsable(updatedMs: Long?, nowMs: Long): Boolean =
        updatedMs != null && updatedMs > 0 && nowMs - updatedMs <= STALE_MS

    fun label(updatedMs: Long?, nowMs: Long): String {
        if (!isUsable(updatedMs, nowMs)) return UNAVAILABLE
        val age = (nowMs - updatedMs!!).coerceAtLeast(0)
        val min = age / 60_000L
        return when {
            min < 1 -> "Updated just now"
            min < 60 -> "Updated $min min ago"
            else -> "Updated ${min / 60} h ago"
        }
    }
}

/** Tolerant row parsing: numbers may arrive as any Number or String; bad rows are skipped. */
object WeatherParser {
    fun current(row: Map<String, Any?>): WeatherCurrent? {
        val temp = dbl(row["temp_c"])
        if (temp.isNaN()) return null
        val code = int(row["code"], -1)
        val unit = str(row["unit"]).uppercase().takeIf { it == "C" || it == "F" } ?: "C"
        return WeatherCurrent(
            location = str(row["location"]),
            tempC = temp,
            feelsC = dbl(row["feels_c"]),
            code = code,
            condition = str(row["condition"]).ifBlank { WeatherFormat.conditionFor(code) },
            isDay = int(row["is_day"], 1) != 0,
            highC = dbl(row["high_c"]),
            lowC = dbl(row["low_c"]),
            humidity = int(row["humidity"], -1),
            windKmh = dbl(row["wind_kmh"]),
            updatedMs = lng(row["updated_ms"]),
            unit = unit
        )
    }

    fun daily(rows: List<Map<String, Any?>>): List<WeatherDay> = rows.mapNotNull { r ->
        val day = lng(r["day_ms"])
        val hi = dbl(r["high_c"])
        val lo = dbl(r["low_c"])
        if (day <= 0 || hi.isNaN() || lo.isNaN()) return@mapNotNull null
        val code = int(r["code"], -1)
        WeatherDay(day, code, str(r["condition"]).ifBlank { WeatherFormat.conditionFor(code) }, hi, lo)
    }.take(WeatherContract.MAX_DAYS)

    private fun str(v: Any?): String = (v as? String)?.trim() ?: ""
    private fun dbl(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }
    private fun lng(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
    private fun int(v: Any?, def: Int): Int = when (v) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull() ?: def
        else -> def
    }
}

/** Reads a snapshot from any [WeatherSource]. Never throws; null when nothing usable. Call off the main thread. */
class WeatherReader(private val source: WeatherSource) {
    fun read(): WeatherSnapshot? = try {
        val cur = source.queryCurrent()?.firstOrNull()?.let { WeatherParser.current(it) }
        if (cur == null) null else {
            val days = try { source.queryDaily()?.let { WeatherParser.daily(it) } } catch (e: Exception) { null }
            WeatherSnapshot(cur, days ?: emptyList())
        }
    } catch (e: Exception) {
        null
    }
}

/** At most one event per [intervalMs]; used for the REFRESH broadcast. */
class RefreshThrottle(private val intervalMs: Long) {
    private var last = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        if (last != Long.MIN_VALUE && nowMs - last < intervalMs) return false
        last = nowMs
        return true
    }
}
