package dev.tvlauncher.room

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Input seam for sunrise/sunset in LOCAL minutes of the day (sunrise, sunset), or null when unknown.
 * Implementations must be cheap and may be called once a minute on the main thread.
 */
fun interface DaylightSource {
    fun sunriseSunsetMinutes(): Pair<Int, Int>?
}

object DaylightSources {
    /** The first source that answers; a source that throws counts as "no answer". */
    fun firstOf(vararg sources: DaylightSource): DaylightSource = DaylightSource {
        for (s in sources) {
            val r = try { s.sunriseSunsetMinutes() } catch (e: Exception) { null }
            if (r != null) return@DaylightSource r
        }
        null
    }
}

/**
 * Offline estimate from a configured latitude ([SolarEstimator]). [longitudeDeg] null means "use the
 * time zone's meridian" (error: the city's distance from that meridian, 4 min per degree).
 */
class EstimatedDaylight(
    private val latitudeDeg: Double,
    private val longitudeDeg: Double?,
    private val utcOffsetMinutes: () -> Int,
    private val dayOfYear: () -> Int,
    private val dstMinutes: () -> Int = { 0 }
) : DaylightSource {
    override fun sunriseSunsetMinutes(): Pair<Int, Int>? =
        SolarEstimator.sunriseSunsetClock(dayOfYear(), latitudeDeg, longitudeDeg, utcOffsetMinutes(), dstMinutes())

    companion object {
        /** The estimate for [s]'s location on this device's clock and zone, or null when no location is set. */
        fun forSettings(s: RoomSettings): EstimatedDaylight? {
            val lat = s.latitudeDeg ?: return null
            return EstimatedDaylight(
                latitudeDeg = lat,
                longitudeDeg = s.longitudeDeg,
                // Zone offset (incl. DST) at this instant, from the device TimeZone.
                utcOffsetMinutes = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 },
                dayOfYear = { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) },
                dstMinutes = {
                    val tz = java.util.TimeZone.getDefault()
                    (tz.getOffset(System.currentTimeMillis()) - tz.rawOffset) / 60_000
                }
            )
        }
    }
}

/**
 * Low-precision sunrise/sunset (NOAA "General Solar Position" approximations: Fourier series for
 * declination and the equation of time, zenith 90.833 deg for refraction + solar disc). Pure.
 *
 * Accuracy (approximate): the astronomy itself is within ~3 min below the polar circles
 * (cross-checked in tests against an independent Julian-day formulation). The clock-time error is
 * dominated by longitude: about +/-10 min with the real longitude; without it the zone meridian is
 * assumed, i.e. 4 min per degree between the place and its zone meridian (~20 min for Delhi,
 * up to ~40 min for places far west/east inside a wide zone). Solar-time correction:
 * clock = solar + (zoneMeridian - longitude) / 15 h - equationOfTime, with the zone offset (incl.
 * DST) taken at the instant. Returns null for polar day/night or invalid input.
 */
object SolarEstimator {
    private const val ZENITH_DEG = 90.833

    /** Sunrise/sunset in local apparent SOLAR time minutes (solar noon = 12:00). */
    fun sunriseSunset(dayOfYear: Int, latitudeDeg: Double): Pair<Int, Int>? {
        val ha = hourAngleDeg(dayOfYear, latitudeDeg) ?: return null
        val rise = (720 - 4 * ha).roundToInt()
        val set = (720 + 4 * ha).roundToInt()
        return if (rise in 0 until 1440 && set in 0 until 1440 && rise < set) rise to set else null
    }

    /**
     * Sunrise/sunset in local CLOCK minutes. [utcOffsetMinutes] is the zone's current offset
     * including DST; [dstMinutes] is the DST part of it (only used to find the zone meridian when
     * [longitudeDeg] is null). East longitudes are positive.
     */
    fun sunriseSunsetClock(
        dayOfYear: Int,
        latitudeDeg: Double,
        longitudeDeg: Double?,
        utcOffsetMinutes: Int,
        dstMinutes: Int = 0
    ): Pair<Int, Int>? {
        val lon = longitudeDeg ?: ((utcOffsetMinutes - dstMinutes) / 4.0)
        if (lon.isNaN() || abs(lon) > 180.0) return null
        val ha = hourAngleDeg(dayOfYear, latitudeDeg) ?: return null
        val eq = equationOfTimeMin(dayOfYear)
        val rise = (720 - 4 * (lon + ha) - eq + utcOffsetMinutes).roundToInt()
        val set = (720 - 4 * (lon - ha) - eq + utcOffsetMinutes).roundToInt()
        if (rise !in 0 until 1440 || set !in 0 until 1440 || rise >= set) return null
        return rise to set
    }

    private fun gamma(dayOfYear: Int) = 2 * PI / 365.0 * (dayOfYear - 1)

    private fun declinationRad(d: Int): Double {
        val g = gamma(d)
        return 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    }

    /** Apparent minus mean solar time, minutes. */
    fun equationOfTimeMin(d: Int): Double {
        val g = gamma(d)
        return 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    }

    private fun hourAngleDeg(dayOfYear: Int, latitudeDeg: Double): Double? {
        if (dayOfYear !in 1..366 || latitudeDeg.isNaN() || abs(latitudeDeg) > 90.0) return null
        if (abs(latitudeDeg) >= 89.99) return null
        val lat = Math.toRadians(latitudeDeg)
        val decl = declinationRad(dayOfYear)
        val c = cos(Math.toRadians(ZENITH_DEG)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        if (c.isNaN() || c < -1.0 || c > 1.0) return null
        return Math.toDegrees(acos(c))
    }
}
