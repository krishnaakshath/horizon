package dev.tvlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WmoCodesTest {
    @Test fun groups() {
        assertEquals(WeatherGroup.CLEAR, WmoCodes.group(0))
        assertEquals(WeatherGroup.CLEAR, WmoCodes.group(1))
        assertEquals(WeatherGroup.PARTLY_CLOUDY, WmoCodes.group(2))
        assertEquals(WeatherGroup.CLOUDY, WmoCodes.group(3))
        assertEquals(WeatherGroup.FOG, WmoCodes.group(45))
        assertEquals(WeatherGroup.FOG, WmoCodes.group(48))
        for (c in listOf(51, 53, 55, 56, 57)) assertEquals(WeatherGroup.DRIZZLE, WmoCodes.group(c))
        for (c in listOf(61, 63, 65, 66, 67, 80, 81, 82)) assertEquals(WeatherGroup.RAIN, WmoCodes.group(c))
        for (c in listOf(71, 73, 75, 77, 85, 86)) assertEquals(WeatherGroup.SNOW, WmoCodes.group(c))
        for (c in listOf(95, 96, 99)) assertEquals(WeatherGroup.STORM, WmoCodes.group(c))
    }

    @Test fun unknownCodes() {
        for (c in listOf(-1, 4, 44, 100, 1000, Int.MIN_VALUE)) {
            assertEquals(WeatherGroup.UNKNOWN, WmoCodes.group(c))
            assertEquals("Unknown", WmoCodes.text(c))
        }
    }

    @Test fun text() {
        assertEquals("Clear sky", WmoCodes.text(0))
        assertEquals("Mainly clear", WmoCodes.text(1))
        assertEquals("Partly cloudy", WmoCodes.text(2))
        assertEquals("Overcast", WmoCodes.text(3))
        assertEquals("Fog", WmoCodes.text(45))
        assertEquals("Light rain", WmoCodes.text(61))
        assertEquals("Heavy snow", WmoCodes.text(75))
        assertEquals("Thunderstorm", WmoCodes.text(95))
    }
}
