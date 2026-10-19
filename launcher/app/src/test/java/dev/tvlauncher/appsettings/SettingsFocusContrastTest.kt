package dev.tvlauncher.appsettings

import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Theme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Settings focus and selection state keeps its text and icons at >= 4.5:1 in every room
 * palette and for every accent: focused rows, entries and group headers (one style), the current
 * sidebar entry and resting rows/headers on the frosted cards.
 */
class SettingsFocusContrastTest {
    private val accents = AppearanceSettings.PRESETS + AppearanceSettings.DEFAULT_ACCENT +
        listOf(0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFF1C1C1E.toInt())

    @Test
    fun focusedRowsEntriesAndHeaders_whiteInkOnAccentLitGlass() {
        for (a in accents) {
            val fill = SettingsStyle(a, null).focusFill
            for (ink in listOf(Theme.TEXT, Theme.TEXT_SUB_FOCUSED)) {
                val c = Contrast.ratio(ink, fill)
                assertTrue("accent ${Integer.toHexString(a)} ink ${Integer.toHexString(ink)}: $c", c >= 4.5)
            }
        }
    }

    @Test
    fun restingAndCurrentEntries_onTheFrostedSidebar_inEveryRoom() {
        for (m in RoomMode.values()) {
            val p = Palettes.of(m)
            for (bd in listOf(p.backdropTop, p.backdropBottom, 0xFFFFFFFF.toInt())) {
                val card = GlassTokens.surfaceOn(GlassTokens.tint(p, bd), bd)
                assertTrue("$m rest", Contrast.ratio(Theme.TEXT_2, card) >= 4.5)
                val current = Contrast.over(Glass.SELECTED, card)
                assertTrue("$m current", Contrast.ratio(Theme.TEXT, current) >= 4.5)
                assertTrue("$m current dim", Contrast.ratio(Theme.TEXT_2, current) >= 4.5)
            }
        }
    }
}
