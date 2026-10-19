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
            clock24h = false,
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
        assertEquals(false, a.clock24h)
        assertEquals(AppearanceSettings().fixedAccent, a.fixedAccent)
    }

    @Test
    fun appearance_transparentAccent_forcedOpaque() {
        assertEquals(0xFF0000FF.toInt(), AppearanceSettings.fromJson("""{"fixedAccent":255}""").fixedAccent)
        assertEquals(0xFF4C8DFF.toInt(), AppearanceSettings.fromJson("""{"fixedAccent":${0x004C8DFF}}""").fixedAccent)
    }
}
