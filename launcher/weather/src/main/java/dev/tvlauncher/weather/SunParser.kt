package dev.tvlauncher.weather

import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Sunrise/sunset from Open-Meteo's `daily=sunrise,sunset`. Pure, never throws.
 *
 * With `timezone=auto` Open-Meteo sends local wall-clock strings without an offset
 * ("2026-10-20T06:31", seconds optional) in the place's zone. They are turned into epoch ms
 * using, in order: an explicit offset in the string ("Z", "+05:30", "-0400"), the response's
 * IANA `timezone` (exact across DST changes) when this device knows it, else the response's
 * single `utc_offset_seconds`. A plain number is read as epoch seconds (`timeformat=unixtime`).
 *
 * A day only gets sun times when both are present and form a real day: sunrise on the entry's
 * own date, sunset after sunrise, and less than 24 h apart. Polar night/day (missing values,
 * sunrise == sunset, or a 24 h "day" from midnight to midnight) gives null for both.
 */
object SunParser {
    private val LOCAL = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$""")
    private const val DAY_MS = 86_400_000L

    /** The place's zone: the IANA id when known on this device, else null (use the offset). */
    fun zoneOf(timezoneId: String?): TimeZone? {
        if (timezoneId.isNullOrBlank()) return null
        val tz = TimeZone.getTimeZone(timezoneId)
        // Unknown ids come back as "GMT"; only accept a real match.
        return if (tz.id == timezoneId) tz else null
    }

    /** One timestamp -> epoch ms, or null when malformed / impossible. */
    fun epochMs(v: Any?, zone: TimeZone?, offsetSec: Long): Long? {
        if (v is Number) {
            val d = v.toDouble()
            if (!d.isFinite() || d <= 0.0 || d > 1e11) return null
            return (d * 1000.0).toLong()
        }
        val s = (v as? String)?.trim() ?: return null
        val m = LOCAL.matchEntire(s) ?: return null
        val g = m.groupValues
        val y = g[1].toInt(); val mo = g[2].toInt(); val d = g[3].toInt()
        val h = g[4].toInt(); val mi = g[5].toInt(); val sec = g[6].ifEmpty { "0" }.toInt()
        val midnight = OpenMeteoParser.civilUtcMs(y, mo, d) ?: return null
        if (h > 23 || mi > 59 || sec > 59) return null
        val explicit = g[7]
        if (explicit.isNotEmpty()) {
            val off = explicitOffsetSec(explicit) ?: return null
            return midnight + clockMs(h, mi, sec) - off * 1000L
        }
        if (zone != null) {
            val cal = GregorianCalendar(zone)
            cal.clear()
            cal.set(y, mo - 1, d, h, mi, sec)
            return cal.timeInMillis
        }
        return midnight + clockMs(h, mi, sec) - offsetSec * 1000L
    }

    /**
     * Both times of one day, or null for polar day/night and malformed pairs. [dayMs] is the
     * entry's local midnight (see [OpenMeteoParser.dayStartMs]); sunrise must fall on that date.
     */
    fun pair(dayMs: Long, sunrise: Any?, sunset: Any?, zone: TimeZone?, offsetSec: Long): Pair<Long, Long>? {
        val r = epochMs(sunrise, zone, offsetSec) ?: return null
        val s = epochMs(sunset, zone, offsetSec) ?: return null
        if (s <= r || s - r >= DAY_MS - 60_000L) return null
        // dayMs comes from the single utc offset; allow the DST delta (and a little more).
        if (r < dayMs - 2 * 3_600_000L || r >= dayMs + DAY_MS + 2 * 3_600_000L) return null
        return r to s
    }

    /** Stored/served pair check: both or neither, ordered, shorter than a day. */
    fun validStored(sunriseMs: Long?, sunsetMs: Long?): Boolean =
        sunriseMs != null && sunsetMs != null && sunriseMs > 0 && sunsetMs > sunriseMs && sunsetMs - sunriseMs < DAY_MS

    private fun explicitOffsetSec(s: String): Long? {
        if (s == "Z") return 0L
        val sign = if (s[0] == '-') -1 else 1
        val digits = s.substring(1).replace(":", "")
        if (digits.length != 4) return null
        val hh = digits.substring(0, 2).toIntOrNull() ?: return null
        val mm = digits.substring(2).toIntOrNull() ?: return null
        if (hh > 14 || mm > 59) return null
        return sign * (hh * 3600L + mm * 60L)
    }

    private fun clockMs(h: Int, mi: Int, sec: Int): Long = h * 3_600_000L + mi * 60_000L + sec * 1000L
}
