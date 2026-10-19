package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

/** The provider's read-only contract: column names, order, nullability and row shapes. */
class ProviderRowsTest {
    private val place = Place("Mumbai", "Maharashtra", "India", 19.08, 72.88)
    private val sunny = DailyForecast(2000L, 0, 31.0, 24.0, 10_000L, 50_000L)
    private val polar = DailyForecast(1000L, 3, 30.0, 23.0)
    private val snap = WeatherSnapshot(29.0, 31.0, 0, true, 60, 10.0, 31.0, 24.0, 777L, listOf(sunny, polar))

    @Test fun v1ColumnsAreUnchangedAndNewOnesAppended() {
        assertEquals(listOf("location", "temp_c", "feels_c", "code", "condition", "is_day", "high_c", "low_c",
            "humidity", "wind_kmh", "updated_ms", "unit"), WeatherContract.CURRENT_COLUMNS.toList())
        assertEquals(listOf("day_ms", "code", "condition", "high_c", "low_c"), WeatherContract.DAILY_COLUMNS.take(5))
        assertEquals(listOf("sunrise_ms", "sunset_ms"), WeatherContract.DAILY_COLUMNS.drop(5))
        assertEquals(listOf("day_ms", "sunrise_ms", "sunset_ms", "location", "updated_ms"), WeatherContract.SUN_COLUMNS.toList())
        assertEquals("content://dev.tvlauncher.weather/sun", WeatherContract.URI_SUN)
        assertEquals("content://dev.tvlauncher.weather/daily", WeatherContract.URI_DAILY)
        assertEquals("content://dev.tvlauncher.weather/current", WeatherContract.URI_CURRENT)
    }

    @Test fun rowsMatchTheirColumns() {
        val s = WeatherState(place, "C", snap)
        ProviderRows.current(s).forEach { assertEquals(WeatherContract.CURRENT_COLUMNS.size, it.size) }
        ProviderRows.daily(s).forEach { assertEquals(WeatherContract.DAILY_COLUMNS.size, it.size) }
        ProviderRows.sun(s).forEach { assertEquals(WeatherContract.SUN_COLUMNS.size, it.size) }
    }

    @Test fun dailyIsSortedAndCarriesNullableSun() {
        val rows = ProviderRows.daily(WeatherState(place, "C", snap))
        assertEquals(listOf<Any?>(1000L, 3, WmoCodes.text(3), 30.0, 23.0, null, null), rows[0].toList())
        assertEquals(listOf<Any?>(2000L, 0, WmoCodes.text(0), 31.0, 24.0, 10_000L, 50_000L), rows[1].toList())
    }

    @Test fun sunRows() {
        val rows = ProviderRows.sun(WeatherState(place, "C", snap))
        assertEquals(2, rows.size)
        assertEquals(listOf<Any?>(1000L, null, null, "Mumbai", 777L), rows[0].toList())
        assertEquals(listOf<Any?>(2000L, 10_000L, 50_000L, "Mumbai", 777L), rows[1].toList())
    }

    @Test fun sunIsEmptyWithoutCityOrSnapshot() {
        assertTrue(ProviderRows.sun(WeatherState(null, "C", snap)).isEmpty())
        assertTrue(ProviderRows.sun(WeatherState(place, "C", null)).isEmpty())
        assertTrue(ProviderRows.daily(WeatherState(place, "C", null)).isEmpty())
    }

    @Test fun invalidStoredSunIsServedAsNull() {
        val bad = snap.copy(daily = listOf(DailyForecast(1000L, 0, 1.0, 0.0, 50_000L, 10_000L)))
        val row = ProviderRows.sun(WeatherState(place, "C", bad)).single()
        assertNull(row[1]); assertNull(row[2])
    }

    @Test fun currentKeepsItsShapes() {
        assertTrue(ProviderRows.current(WeatherState(null, "C", null)).isEmpty())
        val pending = ProviderRows.current(WeatherState(place, "F", null)).single()
        assertEquals("Mumbai", pending[0]); assertNull(pending[1]); assertEquals(0L, pending[10]); assertEquals("F", pending[11])
        val full = ProviderRows.current(WeatherState(place, "C", snap)).single()
        assertEquals(29.0, full[1]); assertEquals(1, full[5]); assertEquals(777L, full[10])
    }

    @Test fun atMostFiveDays() {
        val many = snap.copy(daily = (1..8).map { DailyForecast(it * 1000L, 0, 1.0, 0.0) })
        assertEquals(5, ProviderRows.sun(WeatherState(place, "C", many)).size)
        assertEquals(5, ProviderRows.daily(WeatherState(place, "C", many)).size)
    }
}
