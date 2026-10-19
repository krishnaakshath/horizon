package dev.tvlauncher.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarEstimatorTest {
    private fun hm(h: Int, m: Int) = h * 60 + m
    private val tol = 15

    private fun assertNear(expected: Int, actual: Int, label: String) =
        assertTrue("$label expected ${fmt(expected)} got ${fmt(actual)}", kotlin.math.abs(expected - actual) <= tol)

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    // Day-of-year for 2026 (not a leap year).
    private val OCT_20 = 293
    private val JUN_21 = 172
    private val DEC_21 = 355
    private val MAR_20 = 79

    @Test fun delhi_solar_time_only() {
        // The latitude-only estimate is local SOLAR time; the brief's check: ~06:25 / ~17:40 (+/- 15 min).
        val (rise, set) = SolarEstimator.sunriseSunset(OCT_20, 28.6)!!
        assertNear(hm(6, 25), rise, "Delhi solar rise")
        assertNear(hm(17, 40), set, "Delhi solar set")
    }

    @Test fun delhi_clock_time() {
        // Published: sunrise 06:25, sunset 17:49 IST.
        val (rise, set) = SolarEstimator.sunriseSunsetClock(OCT_20, 28.61, 77.21, 330)!!
        assertNear(hm(6, 25), rise, "Delhi rise")
        assertNear(hm(17, 49), set, "Delhi set")
    }

    @Test fun kolkata_clock_time() {
        // Published: ~05:38 / ~17:08 IST.
        val (rise, set) = SolarEstimator.sunriseSunsetClock(OCT_20, 22.57, 88.36, 330)!!
        assertNear(hm(5, 38), rise, "Kolkata rise")
        assertNear(hm(17, 8), set, "Kolkata set")
    }

    @Test fun london_solstices() {
        // Summer (BST, UTC+1): 04:43 / 21:21. Winter (GMT): 08:04 / 15:53.
        val (r1, s1) = SolarEstimator.sunriseSunsetClock(JUN_21, 51.51, -0.13, 60)!!
        assertNear(hm(4, 43), r1, "London Jun rise")
        assertNear(hm(21, 21), s1, "London Jun set")
        val (r2, s2) = SolarEstimator.sunriseSunsetClock(DEC_21, 51.51, -0.13, 0)!!
        assertNear(hm(8, 4), r2, "London Dec rise")
        assertNear(hm(15, 53), s2, "London Dec set")
    }

    @Test fun equinox_day_is_about_twelve_hours_everywhere() {
        for (lat in listOf(-50.0, -20.0, 0.0, 20.0, 50.0)) {
            val (r, s) = SolarEstimator.sunriseSunset(MAR_20, lat)!!
            assertTrue("lat $lat len ${s - r}", (s - r) in 715..735)
        }
    }

    @Test fun southern_hemisphere_is_mirrored() {
        val (rn, sn) = SolarEstimator.sunriseSunset(JUN_21, 40.0)!!
        val (rs, ss) = SolarEstimator.sunriseSunset(JUN_21, -40.0)!!
        assertTrue(sn - rn > 14 * 60)
        assertTrue(ss - rs < 10 * 60)
    }

    @Test fun polar_day_and_night_are_null() {
        assertNull(SolarEstimator.sunriseSunset(JUN_21, 78.2))  // Svalbard midnight sun
        assertNull(SolarEstimator.sunriseSunset(DEC_21, 78.2))  // polar night
        assertNull(SolarEstimator.sunriseSunset(DEC_21, -78.2)) // Antarctic summer
        assertNull(SolarEstimator.sunriseSunset(JUN_21, 90.0))
        assertNull(SolarEstimator.sunriseSunset(JUN_21, -90.0))
    }

    @Test fun invalid_inputs_are_null() {
        assertNull(SolarEstimator.sunriseSunset(OCT_20, Double.NaN))
        assertNull(SolarEstimator.sunriseSunset(OCT_20, 91.0))
        assertNull(SolarEstimator.sunriseSunset(0, 28.6))
        assertNull(SolarEstimator.sunriseSunset(367, 28.6))
        assertNull(SolarEstimator.sunriseSunsetClock(OCT_20, 28.6, Double.NaN, 330))
    }

    @Test fun every_day_and_latitude_is_safe() {
        for (d in 1..366) for (lat in -90..90 step 3) {
            val r = SolarEstimator.sunriseSunset(d, lat.toDouble())
            if (r != null) {
                assertTrue(r.first in 0 until 1440 && r.second in 0 until 1440)
                assertTrue(r.first < r.second)
            }
        }
    }

    @Test fun daylight_sources() {
        val fixed = DaylightSource { hm(6, 0) to hm(18, 0) }
        assertEquals(hm(6, 0) to hm(18, 0), fixed.sunriseSunsetMinutes())
        val none = DaylightSource { null }
        assertEquals(fixed.sunriseSunsetMinutes(), DaylightSources.firstOf(none, fixed).sunriseSunsetMinutes())
        assertNull(DaylightSources.firstOf(none, none).sunriseSunsetMinutes())
        val boom = DaylightSource { throw IllegalStateException("provider died") }
        assertEquals(fixed.sunriseSunsetMinutes(), DaylightSources.firstOf(boom, fixed).sunriseSunsetMinutes())
    }

    @Test fun estimated_source_uses_latitude_and_date() {
        val src = EstimatedDaylight(latitudeDeg = 28.61, longitudeDeg = 77.21, utcOffsetMinutes = { 330 }, dayOfYear = { OCT_20 })
        val r = src.sunriseSunsetMinutes()
        assertNotNull(r)
        assertNear(hm(6, 25), r!!.first, "est rise")
        val polar = EstimatedDaylight(latitudeDeg = 80.0, longitudeDeg = null, utcOffsetMinutes = { 60 }, dayOfYear = { JUN_21 })
        assertNull(polar.sunriseSunsetMinutes())
    }

    @Test fun omitting_longitude_reproduces_zone_meridian_behaviour() {
        // Old behaviour (no longitude): the zone meridian = (offset - DST) / 4 degrees.
        for (d in listOf(OCT_20, JUN_21, DEC_21)) {
            assertEquals(SolarEstimator.sunriseSunsetClock(d, 28.61, 82.5, 330), SolarEstimator.sunriseSunsetClock(d, 28.61, null, 330))
            // London in BST: offset 60 with 60 min DST -> meridian 0.
            assertEquals(SolarEstimator.sunriseSunsetClock(d, 51.51, 0.0, 60, 60), SolarEstimator.sunriseSunsetClock(d, 51.51, null, 60, 60))
        }
        // Honest KDoc claim: without longitude, Delhi (5.3 deg west of IST's meridian) is ~21 min early, inside +/-40.
        val (r, _) = SolarEstimator.sunriseSunsetClock(OCT_20, 28.61, null, 330)!!
        assertTrue(fmt(r), kotlin.math.abs(r - hm(6, 25)) in 15..40)
    }

    // ---- Independent cross-check -------------------------------------------------------------
    // Second formulation: the Julian-day "sunrise equation" (Meeus-style mean anomaly, equation
    // of the centre, ecliptic longitude, solar transit; Wikipedia "Sunrise equation"). It shares
    // no code or coefficients with SolarEstimator's NOAA Fourier series.

    private fun reference(y: Int, mo: Int, d: Int, lat: Double, lon: Double, offsetMin: Int): Pair<Int, Int> {
        val jdNoon = 2440587.5 + java.time.LocalDate.of(y, mo, d).toEpochDay() + 0.5
        val n = Math.ceil(jdNoon - 2451545.0 + 0.0008)
        val jStar = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar) % 360.0
        val mr = Math.toRadians(m)
        val c = 1.9148 * Math.sin(mr) + 0.02 * Math.sin(2 * mr) + 0.0003 * Math.sin(3 * mr)
        val lam = Math.toRadians((m + c + 180.0 + 102.9372) % 360.0)
        val jTransit = 2451545.0 + jStar + 0.0053 * Math.sin(mr) - 0.0069 * Math.sin(2 * lam)
        val sinD = Math.sin(lam) * Math.sin(Math.toRadians(23.4397))
        val cosD = Math.cos(Math.asin(sinD))
        val phi = Math.toRadians(lat)
        val cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * sinD) / (Math.cos(phi) * cosD)
        val w = Math.toDegrees(Math.acos(cosW))
        fun toMin(j: Double) = Math.floorMod(Math.round(((j + 0.5) % 1.0) * 1440.0 + offsetMin).toInt(), 1440)
        return toMin(jTransit - w / 360.0) to toMin(jTransit + w / 360.0)
    }

    private fun assertWithin(tolMin: Int, expected: Pair<Int, Int>, actual: Pair<Int, Int>, label: String) {
        assertTrue("$label rise ${fmt(expected.first)} vs ${fmt(actual.first)}", kotlin.math.abs(expected.first - actual.first) <= tolMin)
        assertTrue("$label set ${fmt(expected.second)} vs ${fmt(actual.second)}", kotlin.math.abs(expected.second - actual.second) <= tolMin)
    }

    @Test fun mumbai_withLongitude_withinTenMinutesOfReference() {
        // Mumbai 19.08 N, 72.88 E, IST (UTC+5:30), 20 Oct 2026.
        // Reference (second formulation above): 06:34 / 18:12. Published almanac tables
        // (timeanddate.com, Mumbai, Oct 2026) list ~06:33 / ~18:13 - quoted for context only;
        // the assertion is against the independent computation.
        val ref = reference(2026, 10, 20, 19.08, 72.88, 330)
        val est = SolarEstimator.sunriseSunsetClock(OCT_20, 19.08, 72.88, 330)!!
        assertWithin(10, ref, est, "Mumbai")
        assertWithin(3, hm(6, 34) to hm(18, 12), ref, "Mumbai reference sanity")
    }

    @Test fun delhi_kolkata_london_new_york_with_longitude_match_reference() {
        assertWithin(10, reference(2026, 10, 20, 28.61, 77.21, 330), SolarEstimator.sunriseSunsetClock(OCT_20, 28.61, 77.21, 330)!!, "Delhi")
        assertWithin(10, reference(2026, 10, 20, 22.57, 88.36, 330), SolarEstimator.sunriseSunsetClock(OCT_20, 22.57, 88.36, 330)!!, "Kolkata")
        assertWithin(10, reference(2026, 6, 21, 51.51, -0.13, 60), SolarEstimator.sunriseSunsetClock(JUN_21, 51.51, -0.13, 60)!!, "London Jun")
        assertWithin(10, reference(2026, 12, 21, 51.51, -0.13, 0), SolarEstimator.sunriseSunsetClock(DEC_21, 51.51, -0.13, 0)!!, "London Dec")
        assertWithin(10, reference(2026, 10, 20, 40.71, -74.01, -240), SolarEstimator.sunriseSunsetClock(OCT_20, 40.71, -74.01, -240)!!, "New York")
    }

    @Test fun longitude_correction_is_four_minutes_per_degree() {
        val a = SolarEstimator.sunriseSunsetClock(OCT_20, 20.0, 80.0, 330)!!
        val b = SolarEstimator.sunriseSunsetClock(OCT_20, 20.0, 75.0, 330)!!
        assertEquals(20, b.first - a.first)
        assertEquals(20, b.second - a.second)
    }
}
