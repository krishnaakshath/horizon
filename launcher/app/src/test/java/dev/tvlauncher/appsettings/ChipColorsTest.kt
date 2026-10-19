package dev.tvlauncher.appsettings

import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipColorsTest {
    private val accents = AppearanceSettings.PRESETS + AppearanceSettings.DEFAULT_ACCENT

    /** What a resting row sits on: the frosted pane over the room backdrop (each room's top stop). */
    private val restingRows = RoomMode.values().map { m ->
        val p = Palettes.of(m)
        ChipColors.over(Glass.ROW, GlassTokens.surfaceOn(GlassTokens.tint(p, p.backdropTop), p.backdropTop))
    }

    private fun focusedRow(accent: Int) = SettingsStyle(accent, null).focusFill

    @Test
    fun positiveChip_textIsBright_andReadable_forEveryAccent() {
        for (a in accents) for (focused in listOf(false, true)) {
            val bg = ChipColors.fill(Chip.POSITIVE, a, focused)
            val fg = ChipColors.text(Chip.POSITIVE, rowFocused = focused)
            assertEquals(Theme.TEXT, fg)
            val c = ChipColors.contrast(fg, bg)
            assertTrue("accent ${Integer.toHexString(a)}: $c", c >= 4.5)
        }
    }

    @Test
    fun neutralChip_isReadable_atRestAndFocused() {
        for (row in restingRows) {
            val rest = ChipColors.over(ChipColors.fill(Chip.NEUTRAL, 0), row)
            assertTrue(ChipColors.contrast(ChipColors.text(Chip.NEUTRAL, false), rest) >= 4.5)
        }
        for (a in accents) {
            val bg = ChipColors.over(ChipColors.fill(Chip.NEUTRAL, a, rowFocused = true), focusedRow(a))
            val c = ChipColors.contrast(ChipColors.text(Chip.NEUTRAL, true), bg)
            assertTrue("accent ${Integer.toHexString(a)}: $c", c >= 4.5)
        }
    }

    @Test
    fun contrast_math() {
        assertEquals(21.0, ChipColors.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        val half = ChipColors.over(0x80FFFFFF.toInt(), 0xFF000000.toInt())
        assertEquals(0xFF, (half ushr 24) and 0xFF)
        assertEquals(0x80, (half shr 16) and 0xFF)
    }
}
