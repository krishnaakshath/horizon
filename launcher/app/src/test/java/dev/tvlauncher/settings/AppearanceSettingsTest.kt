package dev.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun appearance_roundTrip() {
        val a = AppearanceSettings(
            accentMode = AccentMode.FIXED,
            fixedAccent = 0xFF22C55E.toInt(),
            animationLevel = AnimationLevel.LITE,
            clockFormat = ClockFormat.H12,
            showDate = false,
            showStatusIcons = false,
            rowTileSize = TileSize.LARGE
        )
        assertEquals(a, AppearanceSettings.fromJson(a.toJson()))
        assertEquals(AppearanceSettings(), AppearanceSettings.fromJson(AppearanceSettings().toJson()))
    }

    @Test
    fun appearance_corruptJson_defaults() {
        assertEquals(AppearanceSettings(), AppearanceSettings.fromJson(""))
        assertEquals(AppearanceSettings(), AppearanceSettings.fromJson("{not json"))
        assertEquals(AppearanceSettings(), AppearanceSettings.fromJson("[1,2,3]"))
        assertEquals(AppearanceSettings(), AppearanceSettings.fromJson("null"))
    }

    @Test
    fun appearance_unknownValues_fallBackPerField() {
        val json = """{"accentMode":"RAINBOW","animationLevel":"WARP","rowTileSize":"HUGE",""" +
            """"clock24h":false,"fixedAccent":"oops","future":{"x":1}}"""
        val a = AppearanceSettings.fromJson(json)
        assertEquals(AccentMode.AUTO_BRAND, a.accentMode)
        assertEquals(AnimationLevel.AUTO, a.animationLevel)
        assertEquals(TileSize.MEDIUM, a.rowTileSize)
        assertEquals(ClockFormat.H12, a.clockFormat) // legacy boolean migrated
        assertEquals(AppearanceSettings().fixedAccent, a.fixedAccent)
    }

    @Test
    fun appearance_transparentAccent_forcedOpaque() {
        assertEquals(0xFF0000FF.toInt(), AppearanceSettings.fromJson("""{"fixedAccent":255}""").fixedAccent)
        assertEquals(0xFF4C8DFF.toInt(), AppearanceSettings.fromJson("""{"fixedAccent":${0x004C8DFF}}""").fixedAccent)
    }

    @Test
    fun clockFormat_defaultsToAuto() {
        assertEquals(ClockFormat.AUTO, AppearanceSettings().clockFormat)
        assertEquals(ClockFormat.AUTO, AppearanceSettings.fromJson("{}").clockFormat)
        assertEquals(ClockFormat.AUTO, AppearanceSettings.fromJson("{corrupt").clockFormat)
    }

    @Test
    fun clockFormat_migratesLegacyBoolean() {
        assertEquals(ClockFormat.H24, AppearanceSettings.fromJson("""{"clock24h":true}""").clockFormat)
        assertEquals(ClockFormat.H12, AppearanceSettings.fromJson("""{"clock24h":false}""").clockFormat)
        assertEquals(ClockFormat.AUTO, AppearanceSettings.fromJson("""{"clock24h":"yes"}""").clockFormat)
        // The new key wins over the legacy one.
        assertEquals(ClockFormat.AUTO, AppearanceSettings.fromJson("""{"clockFormat":"AUTO","clock24h":true}""").clockFormat)
        assertEquals(ClockFormat.AUTO, AppearanceSettings.fromJson("""{"clockFormat":"SOLAR"}""").clockFormat)
    }

    @Test
    fun clockFormat_roundTripsEveryValue() {
        for (f in ClockFormat.values()) {
            assertEquals(f, AppearanceSettings.fromJson(AppearanceSettings(clockFormat = f).toJson()).clockFormat)
        }
    }
}
