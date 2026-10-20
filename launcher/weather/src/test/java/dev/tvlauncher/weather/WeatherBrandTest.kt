package dev.tvlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherBrandTest {
    @Test fun palette() {
        assertEquals(0xFF0B0D12.toInt(), WeatherBrand.BG)
        assertEquals(0xFF151922.toInt(), WeatherBrand.SURFACE)
        assertEquals(0xFFF5F7FA.toInt(), WeatherBrand.TEXT)
        assertEquals("M02 Weather", WeatherBrand.APP_NAME)
    }
}
