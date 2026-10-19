package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherNullHighLowTest {
    private val now = 1_800_000_000_000L
    private val row = mapOf<String, Any?>(
        "location" to "Pune", "temp_c" to 21.4, "code" to 0L, "is_day" to 1L,
        "high_c" to null, "low_c" to null, "updated_ms" to now - 60_000L, "unit" to "C"
    )

    @Test fun cursorRow_nullHighLow_parsesAsNull() {
        val c = WeatherParser.current(row)!!
        assertNull(c.highC)
        assertNull(c.lowC)
        // Present values still parse; a single null side is kept null.
        val one = WeatherParser.current(row + ("high_c" to 27.0))!!
        assertEquals(27.0, one.highC!!, 0.0)
        assertNull(one.lowC)
        assertNull(WeatherParser.current(row + ("high_c" to "n/a"))!!.highC)
    }

    @Test fun layout_withoutHighLow_hasNoHighLowBlock() {
        val snap = WeatherSnapshot(WeatherParser.current(row)!!, emptyList())
        val d = WeatherDisplay.from(snap, now)
        assertTrue(d.available)
        assertNull(d.highLow)
        assertEquals("21°", d.temp)
        val withHl = WeatherDisplay.from(WeatherSnapshot(WeatherParser.current(row + ("high_c" to 27.0) + ("low_c" to 16.0))!!, emptyList()), now)
        assertEquals("H 27°  L 16°", withHl.highLow)
    }

    @Test fun format_neverPrintsNull() {
        assertNull(WeatherFormat.highLow(null, 12.0, "C"))
        assertNull(WeatherFormat.highLow(20.0, null, "F"))
        assertNull(WeatherFormat.highLow(null, null, "C"))
        assertEquals("–", WeatherFormat.temp(null, "C"))
        val snap = WeatherSnapshot(WeatherParser.current(row + ("location" to null) + ("condition" to null))!!, emptyList())
        val d = WeatherDisplay.from(snap, now)
        for (s in listOf(d.temp, d.condition, d.highLow, d.location, d.freshness)) {
            assertFalse("printed null: $s", s?.contains("null") == true)
        }
    }

    @Test fun zeroRows_isUnavailable() {
        val r = WeatherReader(object : WeatherSource {
            override fun queryCurrent() = emptyList<Map<String, Any?>>()
            override fun queryDaily() = emptyList<Map<String, Any?>>()
        }).read()
        assertNull(r)
        val d = WeatherDisplay.from(r, now)
        assertFalse(d.available)
        assertEquals("Weather unavailable", d.condition)
        assertNull(d.temp); assertNull(d.highLow)
    }

    @Test fun unitF_convertsCelsiusValues() {
        val snap = WeatherSnapshot(WeatherParser.current(row + ("unit" to "F") + ("high_c" to 30.0) + ("low_c" to 20.0))!!, emptyList())
        val d = WeatherDisplay.from(snap, now)
        assertEquals("71°", d.temp)
        assertEquals("H 86°  L 68°", d.highLow)
        assertNotNull(d.icon)
    }
}
