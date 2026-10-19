package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

class UrlTest {
    @Test fun forecastUrl() {
        val u = OpenMeteoClient.forecastUrl(-33.8688, 151.2093)
        assertTrue(u.startsWith("https://api.open-meteo.com/v1/forecast?latitude=-33.8688&longitude=151.2093&current="))
        assertTrue(u.endsWith("&timezone=auto&forecast_days=5&wind_speed_unit=kmh"))
    }

    @Test fun searchEncoding() {
        fun name(q: String) = OpenMeteoClient.searchUrl(q).substringAfter("name=").substringBefore("&count")
        assertEquals("New+York", name("  New York "))
        assertEquals("a%26b", name("a&b"))
        assertEquals("a%23b", name("a#b"))
        assertEquals("Z%C3%BCrich", name("Zürich"))
        assertEquals("%F0%9F%8C%A7", name("🌧"))
        assertTrue(OpenMeteoClient.searchUrl("x").startsWith("https://geocoding-api.open-meteo.com/v1/search?name=x&count=8&language=en&format=json"))
    }

    @Test fun queryCappedAt64() {
        assertEquals(64, OpenMeteoClient.normalizeQuery("a".repeat(500)).length)
        // emoji straddling the cap must not leave a lone surrogate
        val q = "a".repeat(63) + "🌧"
        val n = OpenMeteoClient.normalizeQuery(q)
        assertEquals(63, n.length)
        assertTrue(OpenMeteoClient.searchUrl(q).contains("name=" + "a".repeat(63) + "&count"))
        assertEquals("", OpenMeteoClient.normalizeQuery("   "))
    }
}
