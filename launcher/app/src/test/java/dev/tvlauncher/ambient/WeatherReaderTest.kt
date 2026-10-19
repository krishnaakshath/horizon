package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherReaderTest {
    private val currentRow = mapOf<String, Any?>(
        "location" to "Pune", "temp_c" to 21.4, "feels_c" to 20.0, "code" to 2L,
        "condition" to "Partly cloudy", "is_day" to 1, "high_c" to 27.0, "low_c" to 16.5,
        "humidity" to 64, "wind_kmh" to 11.2, "updated_ms" to 1_800_000_000_000L, "unit" to "C"
    )

    private fun day(i: Int) = mapOf<String, Any?>(
        "day_ms" to 1_800_000_000_000L + i * 86_400_000L, "code" to 61, "condition" to "Rain",
        "high_c" to 20.0 + i, "low_c" to 10.0 + i
    )

    private class FakeSource(
        val current: List<Map<String, Any?>>?,
        val daily: List<Map<String, Any?>>?,
        val throwOn: Boolean = false
    ) : WeatherSource {
        override fun queryCurrent(): List<Map<String, Any?>>? { if (throwOn) throw IllegalStateException("x"); return current }
        override fun queryDaily(): List<Map<String, Any?>>? { if (throwOn) throw SecurityException("x"); return daily }
    }

    @Test fun parseCurrent_allColumns() {
        val c = WeatherParser.current(currentRow)!!
        assertEquals("Pune", c.location)
        assertEquals(21.4, c.tempC, 0.0)
        assertEquals(2, c.code)
        assertTrue(c.isDay)
        assertEquals(64, c.humidity)
        assertEquals(1_800_000_000_000L, c.updatedMs)
        assertEquals("C", c.unit)
    }

    @Test fun parseCurrent_numbersAsStrings_andMissingOptionals() {
        val c = WeatherParser.current(mapOf("temp_c" to "18.5", "code" to "3", "is_day" to "0", "updated_ms" to "5"))!!
        assertEquals(18.5, c.tempC, 0.0)
        assertEquals(3, c.code)
        assertFalse(c.isDay)
        assertEquals("", c.location)
        assertNull(c.highC)
        assertEquals("C", c.unit)
        assertEquals("Overcast", c.condition) // condition falls back to the WMO text
    }

    @Test fun parseCurrent_missingTemp_isNull() {
        assertNull(WeatherParser.current(mapOf("location" to "X")))
        assertNull(WeatherParser.current(mapOf("temp_c" to "warm")))
    }

    @Test fun parseCurrent_badUnit_defaultsToC() {
        assertEquals("C", WeatherParser.current(currentRow + ("unit" to "kelvin"))!!.unit)
        assertEquals("F", WeatherParser.current(currentRow + ("unit" to "f"))!!.unit)
    }

    @Test fun parseDaily_skipsBadRows_capsAtFive() {
        val rows = (0 until 7).map { day(it) } + listOf(mapOf<String, Any?>("code" to 1))
        val d = WeatherParser.daily(rows)
        assertEquals(5, d.size)
        assertEquals(20.0, d[0].highC, 0.0)
        assertEquals(WxIcon.RAIN, WeatherFormat.iconFor(d[0].code, true))
    }

    @Test fun reader_combinesCurrentAndDaily() {
        val snap = WeatherReader(FakeSource(listOf(currentRow), (0 until 5).map { day(it) })).read()!!
        assertEquals("Pune", snap.current.location)
        assertEquals(5, snap.daily.size)
    }

    @Test fun reader_providerMissing_isNull() {
        assertNull(WeatherReader(FakeSource(null, null)).read())
        assertNull(WeatherReader(FakeSource(emptyList(), emptyList())).read())
    }

    @Test fun reader_neverThrows() {
        assertNull(WeatherReader(FakeSource(listOf(currentRow), null, throwOn = true)).read())
    }

    @Test fun reader_dailyMissing_stillReturnsCurrent() {
        val snap = WeatherReader(FakeSource(listOf(currentRow), null)).read()!!
        assertTrue(snap.daily.isEmpty())
    }

    @Test fun refreshThrottle_atMostEvery15Min() {
        val t = RefreshThrottle(15 * 60_000L)
        assertTrue(t.tryAcquire(1_000L))
        assertFalse(t.tryAcquire(1_000L + 14 * 60_000L))
        assertTrue(t.tryAcquire(1_000L + 15 * 60_000L))
        assertFalse(t.tryAcquire(1_000L + 15 * 60_000L + 1))
    }

    /** Expected values are copied from the binding contract text, not from WeatherContract. */
    @Test fun contract_matchesBindingSpec() {
        assertEquals("content://dev.tvlauncher.weather/current", WeatherContract.CURRENT_URI)
        assertEquals("content://dev.tvlauncher.weather/daily", WeatherContract.DAILY_URI)
        assertEquals("dev.tvlauncher.weather.action.REFRESH", WeatherContract.ACTION_REFRESH)
        assertEquals("dev.tvlauncher.weather.action.SETTINGS", WeatherContract.ACTION_SETTINGS)
        assertEquals("dev.tvlauncher.weather", WeatherContract.PACKAGE)
        assertEquals(
            listOf("location", "temp_c", "feels_c", "code", "condition", "is_day", "high_c", "low_c",
                "humidity", "wind_kmh", "updated_ms", "unit"),
            WeatherContract.CURRENT_COLUMNS
        )
        assertEquals(listOf("day_ms", "code", "condition", "high_c", "low_c"), WeatherContract.DAILY_COLUMNS)
    }

    @Test fun parser_readsEveryContractColumn() {
        // A row keyed only by the literal contract column names fills every field.
        val c = WeatherParser.current(mapOf("location" to "L", "temp_c" to 1.0, "feels_c" to 2.0, "code" to 3,
            "condition" to "C", "is_day" to 0, "high_c" to 4.0, "low_c" to 5.0, "humidity" to 6,
            "wind_kmh" to 7.0, "updated_ms" to 8L, "unit" to "F"))!!
        assertEquals(WeatherCurrent("L", 1.0, 2.0, 3, "C", false, 4.0, 5.0, 6, 7.0, 8L, "F"), c)
        val d = WeatherParser.daily(listOf(mapOf("day_ms" to 9L, "code" to 61, "condition" to "R", "high_c" to 1.5, "low_c" to 0.5)))
        assertEquals(listOf(WeatherDay(9L, 61, "R", 1.5, 0.5)), d)
    }
}
