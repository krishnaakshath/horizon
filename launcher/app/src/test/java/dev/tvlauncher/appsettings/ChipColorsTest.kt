package dev.tvlauncher.appsettings

import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.ui.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipColorsTest {
    private val accents = AppearanceSettings.PRESETS + AppearanceSettings.DEFAULT_ACCENT

    /** What a row sits on: the glass pane over the base color. */
    private val pane = ChipColors.over(Glass.PANEL, Theme.BASE)
    private val restingRow = ChipColors.over(Glass.ROW, pane)

    private fun focusedRow(accent: Int) = SettingsStyle(accent, null).tint(0.26f)

    @Test
    fun positiveChip_textIsBright_andReadable_forEveryAccent() {
        for (a in accents) {
            val bg = ChipColors.fill(Chip.POSITIVE, a)
            val fg = ChipColors.text(Chip.POSITIVE, rowFocused = false)
            assertEquals(Theme.TEXT, fg)
            val c = ChipColors.contrast(fg, bg)
            assertTrue("accent ${Integer.toHexString(a)}: $c", c >= 4.5)
        }
    }

    @Test
    fun neutralChip_isReadable_atRestAndFocused() {
        val rest = ChipColors.over(ChipColors.fill(Chip.NEUTRAL, 0), restingRow)
        assertTrue(ChipColors.contrast(ChipColors.text(Chip.NEUTRAL, false), rest) >= 4.5)
        for (a in accents) {
            val bg = ChipColors.over(ChipColors.fill(Chip.NEUTRAL, a), focusedRow(a))
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
