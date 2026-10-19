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
        // The latitude-only estimate is local SOLAR time; the brief's check: ~06:25 / ~17:40 (+/- 20 min).
        val (rise, set) = SolarEstimator.sunriseSunset(OCT_20, 28.6)!!
        assertTrue(fmt(rise), kotlin.math.abs(rise - hm(6, 25)) <= 20)
        assertTrue(fmt(set), kotlin.math.abs(set - hm(17, 40)) <= 20)
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

    @Test fun zone_meridian_used_when_longitude_unknown() {
        // India: zone meridian 82.5 E. Delhi is 5.3 deg west of it, so ~21 min later than the estimate.
        val (r, _) = SolarEstimator.sunriseSunsetClock(OCT_20, 28.61, null, 330)!!
        assertTrue(fmt(r), kotlin.math.abs(r - hm(6, 25)) <= 25)
    }
}
