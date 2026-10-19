package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

class StateCodecTest {
    private val snap = WeatherSnapshot(
        tempC = 10.5, feelsC = 9.0, code = 61, isDay = false, humidity = 80, windKmh = 12.5,
        highC = 12.0, lowC = 3.0, updatedMs = 123456789012L,
        daily = listOf(DailyForecast(1000L, 3, 11.0, 2.0)),
    )
    private val place = Place("Berlin", "Berlin", "Germany", 52.52, 13.41)

    @Test fun roundTrip() {
        val s = WeatherState(place, "F", snap)
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }

    @Test fun roundTripEmpty() {
        val s = WeatherState(null, "C", null)
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }

    @Test fun garbageGivesDefault() {
        val d = WeatherState(null, "C", null)
        assertEquals(d, StateCodec.decode(null))
        assertEquals(d, StateCodec.decode(""))
        assertEquals(d, StateCodec.decode("{{{"))
        assertEquals(d, StateCodec.decode("[1]"))
    }

    @Test fun badUnitAndBadPlaceTolerated() {
        val s = StateCodec.decode("""{"unit":"K","place":{"name":"X","lat":999,"lon":0},"snapshot":{"tempC":1}}""")
        assertEquals("C", s.unit)
        assertNull(s.place)
        assertNull(s.snapshot)
    }
}
