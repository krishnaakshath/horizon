package dev.tvlauncher.room

import java.util.TimeZone

/** One row of the weather companion's `content://dev.tvlauncher.weather/sun` (epoch ms, null = none). */
data class SunRow(
    val dayMs: Long,
    val sunriseMs: Long?,
    val sunsetMs: Long?,
    val location: String?,
    val updatedMs: Long
)

/**
 * One read of the companion. [installed]: the provider resolves. [supported]: it serves sun
 * times (an older companion answers /sun with no sun columns). [rows]: null when the query
 * failed (keep what we had), empty when no city is chosen or the first update is pending.
 */
data class SunRead(val installed: Boolean, val supported: Boolean, val rows: List<SunRow>?) {
    companion object {
        val MISSING = SunRead(installed = false, supported = false, rows = emptyList())
        val FAILED = SunRead(installed = true, supported = true, rows = null)
    }
}

/** Sunrise and sunset in LOCAL minutes of this device's day. */
data class SunTimes(val sunriseMin: Int, val sunsetMin: Int) {
    fun pair(): Pair<Int, Int> = sunriseMin to sunsetMin
}

/** Why the weather companion did or did not give today's sun times. */
enum class WeatherSunState { LOADING, OK, NOT_INSTALLED, UPDATE_NEEDED, NO_CITY, NO_TIMES }

data class WeatherSunStatus(val state: WeatherSunState, val city: String?, val times: SunTimes?)

/** Pure helpers for the weather companion's sun rows. */
object WeatherSun {
    const val COL_DAY_MS = "day_ms"
    const val COL_SUNRISE_MS = "sunrise_ms"
    const val COL_SUNSET_MS = "sunset_ms"
    const val COL_LOCATION = "location"
    const val COL_UPDATED_MS = "updated_ms"
    val COLUMNS = listOf(COL_DAY_MS, COL_SUNRISE_MS, COL_SUNSET_MS, COL_LOCATION, COL_UPDATED_MS)

    /** A row of a neighbouring day stands in for a missing today for at most this many days. */
    const val NEAREST_DAYS = 2
    private const val DAY_MS = 86_400_000L

    /** Tolerant: numbers may arrive as any Number or String; rows without a day are skipped. */
    fun parse(rows: List<Map<String, Any?>>): List<SunRow> = rows.mapNotNull { r ->
        val day = lng(r[COL_DAY_MS]) ?: return@mapNotNull null
        if (day <= 0L) return@mapNotNull null
        var rise = lng(r[COL_SUNRISE_MS])
        var set = lng(r[COL_SUNSET_MS])
        if (rise == null || set == null || rise <= 0L || set <= rise || set - rise >= DAY_MS) { rise = null; set = null }
        SunRow(day, rise, set, (r[COL_LOCATION] as? String)?.trim()?.takeIf { it.isNotEmpty() }, lng(r[COL_UPDATED_MS]) ?: 0L)
    }

    /**
     * Plausible for the Room schedule: the same windows [RoomLightModel.scheduleMode] accepts
     * (sunrise 03:00-09:30, sunset 15:00-21:30, sunrise first). Anything else (a far-away city,
     * a wrong device clock zone, midnight sun) is rejected so the next source is used.
     */
    fun sane(t: SunTimes?): Boolean = t != null && t.sunriseMin in RoomLightModel.SUNRISE_RANGE &&
        t.sunsetMin in RoomLightModel.SUNSET_RANGE && t.sunriseMin < t.sunsetMin

    /** Days since the epoch in [zone] (local calendar day of [ms]). */
    fun localDay(ms: Long, zone: TimeZone): Long = Math.floorDiv(ms + zone.getOffset(ms), DAY_MS)

    fun localMinute(ms: Long, zone: TimeZone): Int =
        Math.floorMod(Math.floorDiv(ms + zone.getOffset(ms), 60_000L), 1440L).toInt()

    /**
     * Today's sun times on this device's clock, or null. The row for today's date wins, even when
     * it has no times (polar day/night: never borrow a neighbour). Without a row for today (data
     * a few days old) the nearest day within [NEAREST_DAYS] stands in: sunrise moves by at most
     * a few minutes a day outside the polar regions. The result is always [sane] or null.
     */
    fun pick(rows: List<SunRow>?, nowMs: Long, zone: TimeZone): SunTimes? {
        if (rows.isNullOrEmpty()) return null
        val today = localDay(nowMs, zone)
        // dayMs is the place's local midnight; its noon is safely inside the same date here.
        fun dayOf(r: SunRow) = localDay(r.dayMs + DAY_MS / 2, zone)
        val exact = rows.firstOrNull { dayOf(it) == today }
        val row = exact ?: rows.filter { it.sunriseMs != null && Math.abs(dayOf(it) - today) <= NEAREST_DAYS }
            .minByOrNull { Math.abs(dayOf(it) - today) } ?: return null
        val rise = row.sunriseMs ?: return null
        val set = row.sunsetMs ?: return null
        val t = SunTimes(localMinute(rise, zone), localMinute(set, zone))
        return t.takeIf { sane(it) }
    }

    /** What Settings shows about the companion. */
    fun status(read: SunRead?, nowMs: Long, zone: TimeZone): WeatherSunStatus {
        if (read == null) return WeatherSunStatus(WeatherSunState.LOADING, null, null)
        if (!read.installed) return WeatherSunStatus(WeatherSunState.NOT_INSTALLED, null, null)
        if (!read.supported) return WeatherSunStatus(WeatherSunState.UPDATE_NEEDED, null, null)
        val rows = read.rows ?: return WeatherSunStatus(WeatherSunState.NO_TIMES, null, null)
        if (rows.isEmpty()) return WeatherSunStatus(WeatherSunState.NO_CITY, null, null)
        val city = rows.firstNotNullOfOrNull { it.location }
        val t = pick(rows, nowMs, zone)
        return WeatherSunStatus(if (t != null) WeatherSunState.OK else WeatherSunState.NO_TIMES, city, t)
    }

    private fun lng(v: Any?): Long? = when (v) {
        is Number -> v.toDouble().takeIf { it.isFinite() }?.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }
}

/** Where AUTO's sun times come from right now (for Settings > Room lighting). */
enum class SunSource { WEATHER, ESTIMATE, FIXED }

data class SunPlan(val source: SunSource, val times: SunTimes?, val weather: WeatherSunStatus)

/**
 * The fallback chain, in order: the weather companion's real times, then the offline estimate
 * for the location set in Room lighting (its longitude, or the time zone's meridian), then the
 * fixed table. Each step must be [WeatherSun.sane]; the result always names a source.
 */
object SunChain {
    fun resolve(weather: WeatherSunStatus, estimate: Pair<Int, Int>?): SunPlan {
        weather.times?.takeIf { weather.state == WeatherSunState.OK && WeatherSun.sane(it) }
            ?.let { return SunPlan(SunSource.WEATHER, it, weather) }
        estimate?.let { SunTimes(it.first, it.second) }?.takeIf { WeatherSun.sane(it) }
            ?.let { return SunPlan(SunSource.ESTIMATE, it, weather) }
        return SunPlan(SunSource.FIXED, null, weather)
    }
}

/** A [DaylightSource] that only passes [WeatherSun.sane] answers on (so a chain falls through). */
fun DaylightSource.saneOnly(): DaylightSource = DaylightSource {
    sunriseSunsetMinutes()?.takeIf { WeatherSun.sane(SunTimes(it.first, it.second)) }
}
