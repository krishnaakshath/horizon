package dev.tvlauncher.room

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class WeatherSunTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val utcMidnight = 1792454400000L // 2026-10-20T00:00Z
    private val day = utcMidnight - 19_800_000L // 2026-10-20 00:00 IST
    private val rise = utcMidnight + 3_660_000L // 06:31 IST
    private val set = utcMidnight + (12 * 60 + 35) * 60_000L // 18:05 IST
    private val noon = day + 12 * 3_600_000L
    private val dayMs = 86_400_000L

    private fun row(d: Long = day, r: Long? = rise, s: Long? = set) = SunRow(d, r, s, "Mumbai", 1L)

    @Test fun picksTodayInDeviceMinutes() {
        assertEquals(SunTimes(6 * 60 + 31, 18 * 60 + 5), WeatherSun.pick(listOf(row()), noon, ist))
    }

    @Test fun picksTheRowForTodaysDate() {
        val rows = listOf(row(day - dayMs, rise - dayMs - 60_000, set - dayMs + 60_000), row(), row(day + dayMs, rise + dayMs + 60_000, set + dayMs))
        assertEquals(SunTimes(391, 1085), WeatherSun.pick(rows, noon, ist))
        assertEquals(SunTimes(392, 1085), WeatherSun.pick(rows, noon + dayMs, ist))
        assertEquals(SunTimes(390, 1086), WeatherSun.pick(rows, noon - dayMs, ist))
        // Just before and after midnight.
        assertEquals(SunTimes(391, 1085), WeatherSun.pick(rows, day + dayMs - 1, ist))
        assertEquals(SunTimes(392, 1085), WeatherSun.pick(rows, day + dayMs, ist))
    }

    @Test fun nearestDayStandsInForAFewDaysOnly() {
        val rows = listOf(row())
        assertEquals(SunTimes(391, 1085), WeatherSun.pick(rows, noon + 2 * dayMs, ist))
        assertNull(WeatherSun.pick(rows, noon + 3 * dayMs, ist))
        assertNull(WeatherSun.pick(rows, noon - 3 * dayMs, ist))
    }

    @Test fun polarTodayIsNotBorrowedFromANeighbour() {
        val rows = listOf(row(day - dayMs), row(r = null, s = null), row(day + dayMs))
        assertNull(WeatherSun.pick(rows, noon, ist))
    }

    @Test fun deviceZoneDecidesTheClockMinutes() {
        // The same instants on a TV set to UTC: 01:01 and 12:35 -> sunrise outside 03:00-09:30.
        assertNull(WeatherSun.pick(listOf(row()), noon, TimeZone.getTimeZone("UTC")))
        // A TV set to Yangon (UTC+6:30, one hour ahead of IST): 07:31 / 19:05.
        assertEquals(SunTimes(451, 1145), WeatherSun.pick(listOf(row()), noon, TimeZone.getTimeZone("Asia/Yangon")))
    }

    @Test fun saneRejectsImplausibleTimes() {
        assertTrue(WeatherSun.sane(SunTimes(391, 1085)))
        assertFalse(WeatherSun.sane(null))
        assertFalse(WeatherSun.sane(SunTimes(2 * 60, 1085)))  // sunrise before 03:00
        assertFalse(WeatherSun.sane(SunTimes(10 * 60, 1085))) // sunrise after 09:30
        assertFalse(WeatherSun.sane(SunTimes(391, 14 * 60)))  // sunset before 15:00
        assertFalse(WeatherSun.sane(SunTimes(391, 22 * 60)))  // sunset after 21:30 (midnight-sun summer)
        assertFalse(WeatherSun.sane(SunTimes(1085, 391)))
    }

    @Test fun parseIsTolerant() {
        val rows = WeatherSun.parse(listOf(
            mapOf("day_ms" to day, "sunrise_ms" to rise, "sunset_ms" to set, "location" to " Mumbai ", "updated_ms" to 5L),
            mapOf("day_ms" to "$day", "sunrise_ms" to "$rise", "sunset_ms" to set.toDouble()),
            mapOf("day_ms" to day, "sunrise_ms" to set, "sunset_ms" to rise), // reversed -> no times
            mapOf("day_ms" to day, "sunrise_ms" to rise),                     // half -> no times
            mapOf("day_ms" to day, "sunrise_ms" to rise, "sunset_ms" to rise + dayMs), // 24 h -> no times
            mapOf("sunrise_ms" to rise, "sunset_ms" to set),                  // no day -> skipped
            mapOf("day_ms" to "x"),
            mapOf("day_ms" to -1L)
        ))
        assertEquals(5, rows.size)
        assertEquals(SunRow(day, rise, set, "Mumbai", 5L), rows[0])
        assertEquals(SunRow(day, rise, set, null, 0L), rows[1])
        rows.drop(2).forEach { assertNull(it.sunriseMs); assertNull(it.sunsetMs) }
    }

    @Test fun statusExplainsEveryCase() {
        assertEquals(WeatherSunState.LOADING, WeatherSun.status(null, noon, ist).state)
        assertEquals(WeatherSunState.NOT_INSTALLED, WeatherSun.status(SunRead.MISSING, noon, ist).state)
        assertEquals(WeatherSunState.UPDATE_NEEDED, WeatherSun.status(SunRead(true, false, emptyList()), noon, ist).state)
        assertEquals(WeatherSunState.NO_CITY, WeatherSun.status(SunRead(true, true, emptyList()), noon, ist).state)
        assertEquals(WeatherSunState.NO_TIMES, WeatherSun.status(SunRead.FAILED, noon, ist).state)
        val polar = WeatherSun.status(SunRead(true, true, listOf(row(r = null, s = null))), noon, ist)
        assertEquals(WeatherSunState.NO_TIMES, polar.state)
        assertEquals("Mumbai", polar.city)
        val ok = WeatherSun.status(SunRead(true, true, listOf(row())), noon, ist)
        assertEquals(WeatherSunStatus(WeatherSunState.OK, "Mumbai", SunTimes(391, 1085)), ok)
    }
}
