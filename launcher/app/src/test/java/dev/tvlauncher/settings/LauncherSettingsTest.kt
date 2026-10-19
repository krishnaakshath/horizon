package dev.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsTest {
    @Test
    fun roundTrip_preservesAllFields() {
        val original = LauncherSettings(
            version = 1,
            favorites = listOf("com.netflix", "com.youtube"),
            order = listOf("com.netflix", "com.youtube", "com.prime"),
            hidden = setOf("com.ads", "com.bloat"),
            theme = "dark"
        )
        val json = original.toJson()
        val restored = LauncherSettings.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun fromJson_unknownFields_ignored() {
        val json = """{"version":1,"favorites":["com.netflix"],"order":[],"hidden":[],"theme":"dark","unknownField":"ignored","future":123}"""
        val settings = LauncherSettings.fromJson(json)
        assertEquals(1, settings.version)
        assertEquals(listOf("com.netflix"), settings.favorites)
        assertEquals("dark", settings.theme)
    }

    @Test
    fun fromJson_emptyOrCorrupt_returnsDefaults() {
        val emptySettings = LauncherSettings.fromJson("")
        assertEquals(1, emptySettings.version)
        assertEquals(emptyList<String>(), emptySettings.favorites)
        assertEquals(emptyList<String>(), emptySettings.order)
        assertEquals(emptySet<String>(), emptySettings.hidden)
        assertEquals("dark", emptySettings.theme)

        val corruptSettings = LauncherSettings.fromJson("{invalid json")
        assertEquals(1, corruptSettings.version)
        assertEquals(emptyList<String>(), corruptSettings.favorites)
        assertEquals(emptyList<String>(), corruptSettings.order)
        assertEquals(emptySet<String>(), corruptSettings.hidden)
        assertEquals("dark", corruptSettings.theme)
    }

    @Test
    fun fromJson_newerVersion_stillLoadsKnownFields() {
        val json = """{"version":2,"favorites":["com.netflix"],"order":["com.youtube"],"hidden":["com.ads"],"theme":"light","futureField":"ignored"}"""
        val settings = LauncherSettings.fromJson(json)
        assertEquals(2, settings.version)
        assertEquals(listOf("com.netflix"), settings.favorites)
        assertEquals(listOf("com.youtube"), settings.order)
        assertEquals(setOf("com.ads"), settings.hidden)
        assertEquals("light", settings.theme)
    }
}
