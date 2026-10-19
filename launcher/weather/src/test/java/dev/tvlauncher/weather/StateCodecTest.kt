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

    @Test fun nullHighLowRoundTrip() {
        val s = WeatherState(place, "C", snap.copy(highC = null, lowC = null))
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }

    @Test fun implausibleValuesOnDecode() {
        assertNull(StateCodec.decode("""{"snapshot":{"tempC":1e300,"code":1,"updatedMs":1}}""").snapshot)
        val s = StateCodec.decode("""{"snapshot":{"tempC":5,"code":1,"updatedMs":1,"humidity":900,"windKmh":-1e300,"highC":-1e300}}""").snapshot!!
        assertEquals(100, s.humidity)
        assertEquals(0.0, s.windKmh, 0.0)
        assertNull(s.highC)
    }

    @Test fun sunTimesRoundTrip() {
        val s = WeatherState(place, "C", snap.copy(daily = listOf(
            DailyForecast(1000L, 3, 11.0, 2.0, 5_000L, 40_000L), DailyForecast(2000L, 3, 11.0, 2.0))))
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }

    @Test fun stateFromBeforeSunTimesStillDecodes() {
        val old = """{"unit":"C","place":{"name":"Berlin","admin1":null,"country":null,"lat":52.5,"lon":13.4},
          "snapshot":{"tempC":5,"code":1,"updatedMs":9,"daily":[{"dayMs":1000,"code":3,"highC":7,"lowC":1}]}}"""
        val d = StateCodec.decode(old).snapshot!!.daily.single()
        assertNull(d.sunriseMs)
        assertNull(d.sunsetMs)
    }

    @Test fun inconsistentStoredSunIsDropped() {
        val j = """{"snapshot":{"tempC":5,"code":1,"updatedMs":9,"daily":[
          {"dayMs":1000,"code":3,"highC":7,"lowC":1,"sunriseMs":9000,"sunsetMs":2000},
          {"dayMs":2000,"code":3,"highC":7,"lowC":1,"sunriseMs":9000}]}}"""
        val days = StateCodec.decode(j).snapshot!!.daily
        assertEquals(2, days.size)
        days.forEach { assertNull(it.sunriseMs); assertNull(it.sunsetMs) }
    }
}
