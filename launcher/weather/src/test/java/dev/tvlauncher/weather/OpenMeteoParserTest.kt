package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

class OpenMeteoParserTest {
    private val full = """
    {"latitude":52.52,"longitude":13.41,"utc_offset_seconds":7200,"timezone":"Europe/Berlin","extra":{"x":1},
     "current":{"time":"2026-10-19T12:00","temperature_2m":14.3,"apparent_temperature":12.1,
       "relative_humidity_2m":71,"weather_code":61,"wind_speed_10m":18.4,"is_day":1,"unknown":5},
     "daily":{"time":["2026-10-19","2026-10-20","2026-10-21","2026-10-22","2026-10-23"],
       "weather_code":[61,3,0,95,71],
       "temperature_2m_max":[15.2,13.0,16.1,12.0,2.4],
       "temperature_2m_min":[8.1,7.5,6.0,9.9,-1.2]}}
    """.trimIndent()

    @Test fun parsesFull() {
        val s = OpenMeteoParser.parseForecast(full, 1000L)!!
        assertEquals(14.3, s.tempC, 0.0)
        assertEquals(12.1, s.feelsC, 0.0)
        assertEquals(71, s.humidity)
        assertEquals(61, s.code)
        assertEquals(18.4, s.windKmh, 0.0)
        assertTrue(s.isDay)
        assertEquals(15.2, s.highC!!, 0.0)
        assertEquals(8.1, s.lowC!!, 0.0)
        assertEquals(1000L, s.updatedMs)
        assertEquals(5, s.daily.size)
        assertEquals(95, s.daily[3].code)
        assertEquals(-1.2, s.daily[4].lowC, 0.0)
    }

    @Test fun dayIsLocalMidnightEpoch() {
        val s = OpenMeteoParser.parseForecast(full, 0L)!!
        // 2026-10-19 00:00 at UTC+2 == 2026-10-18T22:00Z
        assertEquals(1792360800000L, s.daily[0].dayMs)
        assertEquals(86_400_000L, s.daily[1].dayMs - s.daily[0].dayMs)
    }

    @Test fun missingCurrentOrRequiredIsNull() {
        assertNull(OpenMeteoParser.parseForecast("""{"daily":{}}""", 0))
        assertNull(OpenMeteoParser.parseForecast("""{"current":{"weather_code":1}}""", 0))
        assertNull(OpenMeteoParser.parseForecast("""{"current":{"temperature_2m":1.0}}""", 0))
    }

    @Test fun garbageNeverThrows() {
        for (j in listOf("", "   ", "not json", "[]", "null", "{", "{\"current\":5}", "{\"current\":[]}")) {
            assertNull(j, OpenMeteoParser.parseForecast(j, 0))
        }
    }

    @Test fun defaultsWhenOptionalMissing() {
        val s = OpenMeteoParser.parseForecast("""{"current":{"temperature_2m":5.5,"weather_code":3}}""", 7)!!
        assertEquals(5.5, s.feelsC, 0.0)
        assertEquals(0, s.humidity)
        assertEquals(0.0, s.windKmh, 0.0)
        assertTrue(s.isDay)
        assertNull(s.highC)
        assertNull(s.lowC)
        assertTrue(s.daily.isEmpty())
    }

    @Test fun rejectsNonFiniteTemperature() {
        // org.json parses "NaN"-like strings as strings, a string is not a number
        assertNull(OpenMeteoParser.parseForecast("""{"current":{"temperature_2m":"NaN","weather_code":1}}""", 0))
        assertNull(OpenMeteoParser.parseForecast("""{"current":{"temperature_2m":1e999,"weather_code":1}}""", 0))
    }

    @Test fun skipsBadDailyEntries() {
        val j = """{"current":{"temperature_2m":1.0,"weather_code":1},
          "daily":{"time":["2026-10-19","garbage","2026-10-21"],"weather_code":[1,2,3],
          "temperature_2m_max":[5.0,6.0,null],"temperature_2m_min":[1.0,2.0,3.0]}}"""
        val s = OpenMeteoParser.parseForecast(j, 0)!!
        assertEquals(1, s.daily.size)
        assertEquals(5.0, s.highC!!, 0.0)
    }

    @Test fun capsDailyAtFive() {
        val times = (1..9).joinToString(",") { "\"2026-10-0$it\"" }
        val nums = (1..9).joinToString(",") { "$it" }
        val j = """{"current":{"temperature_2m":1.0,"weather_code":1},
          "daily":{"time":[$times],"weather_code":[$nums],"temperature_2m_max":[$nums],"temperature_2m_min":[$nums]}}"""
        assertEquals(5, OpenMeteoParser.parseForecast(j, 0)!!.daily.size)
    }

    private fun cur(extra: String = "") =
        """"current":{"time":"2026-10-19T12:00","temperature_2m":1.0,"weather_code":1$extra}"""

    @Test fun malformedTodayDoesNotShiftHighLow() {
        val j = """{${cur()},"daily":{"time":["2026-10-19","2026-10-20"],"weather_code":[1,2],
          "temperature_2m_max":[null,9.0],"temperature_2m_min":[1.0,3.0]}}"""
        val s = OpenMeteoParser.parseForecast(j, 0)!!
        assertNull(s.highC)
        assertNull(s.lowC)
        assertEquals(1, s.daily.size)
    }

    @Test fun highLowComesFromEntryMatchingToday() {
        val j = """{${cur()},"daily":{"time":["2026-10-18","2026-10-19"],"weather_code":[1,2],
          "temperature_2m_max":[7.0,9.0],"temperature_2m_min":[1.0,3.0]}}"""
        val s = OpenMeteoParser.parseForecast(j, 0)!!
        assertEquals(9.0, s.highC!!, 0.0)
        assertEquals(3.0, s.lowC!!, 0.0)
    }

    @Test fun rejectsImpossibleDates() {
        assertNull(OpenMeteoParser.dayStartMs("2026-02-31", 0))
        assertNull(OpenMeteoParser.dayStartMs("2027-02-29", 0))
        assertNull(OpenMeteoParser.dayStartMs("2026-13-01", 0))
        assertNull(OpenMeteoParser.dayStartMs("2026-04-31", 0))
        assertNull(OpenMeteoParser.dayStartMs("2026-00-10", 0))
        assertNotNull(OpenMeteoParser.dayStartMs("2028-02-29", 0))
        assertEquals(0L, OpenMeteoParser.dayStartMs("1970-01-01", 0))
        assertEquals(-3_600_000L, OpenMeteoParser.dayStartMs("1970-01-01", 3600))
    }

    @Test fun impossibleDailyDateSkipped() {
        val j = """{${cur()},"daily":{"time":["2026-02-31"],"weather_code":[1],
          "temperature_2m_max":[5.0],"temperature_2m_min":[1.0]}}"""
        assertTrue(OpenMeteoParser.parseForecast(j, 0)!!.daily.isEmpty())
    }

    @Test fun plausibilityRanges() {
        for (t in listOf("1e300", "-1e300", "61", "-91")) {
            assertNull(t, OpenMeteoParser.parseForecast("""{"current":{"temperature_2m":$t,"weather_code":1}}""", 0))
        }
        val s = OpenMeteoParser.parseForecast(
            """{"current":{"temperature_2m":5,"weather_code":1,"apparent_temperature":1e300,
              "relative_humidity_2m":250,"wind_speed_10m":1e300}}""", 0)!!
        assertEquals(5.0, s.feelsC, 0.0)
        assertEquals(100, s.humidity)
        assertEquals(0.0, s.windKmh, 0.0)
        val d = OpenMeteoParser.parseForecast(
            """{${cur()},"daily":{"time":["2026-10-19"],"weather_code":[1],
              "temperature_2m_max":[1e300],"temperature_2m_min":[-1e300]}}""", 0)!!
        assertTrue(d.daily.isEmpty())
        assertNull(d.highC)
    }

    @Test fun hostileNestingDoesNotThrow() {
        val deep = "[".repeat(200_000)
        assertNull(OpenMeteoParser.parseForecast("""{"current":$deep""", 0))
        assertNull(OpenMeteoParser.parseForecast("""{"current":{"a":$deep""", 0))
        assertTrue(GeocodingParser.parse("""{"results":$deep""").isEmpty())
        assertEquals(WeatherState(null, "C", null), StateCodec.decode("""{"place":$deep"""))
    }

    private val mumbai = """
    {"utc_offset_seconds":19800,"timezone":"Asia/Kolkata",
     "current":{"time":"2026-10-20T12:00","temperature_2m":31.0,"weather_code":1},
     "daily":{"time":["2026-10-20","2026-10-21"],"weather_code":[1,2],
       "temperature_2m_max":[33.0,32.5],"temperature_2m_min":[24.0,24.1],
       "sunrise":["2026-10-20T06:31","2026-10-21T06:32"],
       "sunset":["2026-10-20T18:05","2026-10-21T18:04"]}}
    """.trimIndent()

    @Test fun parsesSunriseAndSunset() {
        val s = OpenMeteoParser.parseForecast(mumbai, 0L)!!
        // 06:31 IST == 01:01Z on 2026-10-20 (1792454400000 is 00:00Z).
        assertEquals(1792454400000L + 3_660_000L, s.daily[0].sunriseMs)
        assertEquals(1792454400000L + (12 * 60 + 35) * 60_000L, s.daily[0].sunsetMs)
        assertEquals(s.daily[0].sunriseMs!! + 86_400_000L + 60_000L, s.daily[1].sunriseMs)
    }

    @Test fun responsesWithoutSunStillParse() {
        val s = OpenMeteoParser.parseForecast(full, 0L)!!
        assertEquals(5, s.daily.size)
        s.daily.forEach { assertNull(it.sunriseMs); assertNull(it.sunsetMs) }
    }

    @Test fun polarDaysKeepTheForecastButNoSun() {
        val j = """{"utc_offset_seconds":3600,"timezone":"Europe/Oslo",
          "current":{"time":"2026-12-21T12:00","temperature_2m":-8.0,"weather_code":71},
          "daily":{"time":["2026-12-21","2026-12-22","2026-12-23"],"weather_code":[71,71,3],
            "temperature_2m_max":[-6.0,-5.0,-4.0],"temperature_2m_min":[-12.0,-11.0,-10.0],
            "sunrise":[null,"2026-12-22T00:00","2026-12-23T00:00"],
            "sunset":[null,"2026-12-22T00:00","2026-12-24T00:00"]}}"""
        val s = OpenMeteoParser.parseForecast(j, 0L)!!
        assertEquals(3, s.daily.size)
        s.daily.forEach { assertNull(it.sunriseMs); assertNull(it.sunsetMs) }
        assertEquals(-6.0, s.highC!!, 0.0)
    }

    @Test fun badSunValuesDoNotDropTheDay() {
        val j = """{"utc_offset_seconds":0,"current":{"temperature_2m":5.0,"weather_code":1},
          "daily":{"time":["2026-10-20"],"weather_code":[1],"temperature_2m_max":[6.0],"temperature_2m_min":[1.0],
            "sunrise":["garbage"],"sunset":[42]}}"""
        val d = OpenMeteoParser.parseForecast(j, 0L)!!.daily.single()
        assertNull(d.sunriseMs)
        assertNull(d.sunsetMs)
    }

    @Test fun shortSunArraysAreTolerated() {
        val j = """{"utc_offset_seconds":0,"timezone":"GMT","current":{"temperature_2m":5.0,"weather_code":1},
          "daily":{"time":["2026-10-20","2026-10-21"],"weather_code":[1,1],"temperature_2m_max":[6.0,6.0],
            "temperature_2m_min":[1.0,1.0],"sunrise":["2026-10-20T07:00"],"sunset":["2026-10-20T17:30"]}}"""
        val s = OpenMeteoParser.parseForecast(j, 0L)!!
        assertEquals(1792454400000L + 7 * 3_600_000L, s.daily[0].sunriseMs)
        assertNull(s.daily[1].sunriseMs)
    }
}
