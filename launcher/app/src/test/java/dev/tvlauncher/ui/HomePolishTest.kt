package dev.tvlauncher.ui

import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Visual-doctrine rules that are plain numbers: labels on focus, an opaque Spotlight, the room backdrop. */
class HomePolishTest {
    @Test fun tileLabels_showOnFocusOnly() {
        assertEquals(0f, TileSpec.labelAlpha(focused = false), 0f)
        assertEquals(1f, TileSpec.labelAlpha(focused = true), 0f)
    }

    @Test fun spotlightPanel_isNearOpaque_andTheScrimDimsTheShelf() {
        assertTrue((dev.tvlauncher.panel.PanelStyle.SURFACE ushr 24) >= 0xF0)
        assertTrue((dev.tvlauncher.panel.PanelStyle.SURFACE_LITE ushr 24) >= 0xF0)
        for (full in listOf(true, false)) {
            val a = SpotlightKeys.scrimColor(full) ushr 24
            assertTrue("scrim alpha $a", a >= 0xB0)
        }
        // LITE has no highlight layer, so it dims at least as much as FULL.
        assertTrue((SpotlightKeys.scrimColor(false) ushr 24) >= (SpotlightKeys.scrimColor(true) ushr 24))
    }

    @Test fun heroLeavesRoomForTheSideCard() {
        assertEquals(0, HeroLayout.heroEndMarginPx(false, 400, 32))
        assertEquals(432, HeroLayout.heroEndMarginPx(true, 400, 32))
        assertEquals(0, HeroLayout.heroEndMarginPx(true, 0, 32))
    }

    @Test fun splashHalo_isSubtle() {
        val a = SplashLook.HALO_CENTER ushr 24
        assertTrue(a in 1..0x40)
    }

    private fun stops(mode: RoomMode, accent: Int): IntArray {
        val p = Palettes.of(mode)
        return IntArray(3).also { RoomBackdrop.stops(accent, p.backdropTop, p.backdropBottom, p.backdropFloor, p.accentBlend, it) }
    }

    @Test fun roomBackdrop_neverBelowTheFloor() {
        for (mode in RoomMode.values()) {
            val floor = BrandColors.luminance(Palettes.of(mode).backdropFloor)
            for (accent in listOf(0xFF000000.toInt(), 0xFFE50914.toInt(), 0xFF4C8DFF.toInt(), 0xFFFFFFFF.toInt())) {
                for (c in stops(mode, accent)) assertTrue("$mode ${Integer.toHexString(c)}", BrandColors.luminance(c) >= floor)
            }
        }
    }

    @Test fun roomBackdrop_bottomIsThePalettesBottom_andTheAccentLeadsTopLeft() {
        val accent = 0xFF4C8DFF.toInt()
        for (mode in RoomMode.values()) {
            val s = stops(mode, accent)
            assertEquals(RoomBackdrop.lift(Palettes.of(mode).backdropBottom, Palettes.of(mode).backdropFloor), s[2])
            // The accent mix decreases from the top-left stop to the bottom one.
            val d0 = colorDistance(s[0], accent)
            val d1 = colorDistance(s[1], accent)
            assertTrue("$mode", d0 <= d1)
        }
    }

    @Test fun roomBackdrop_brightRoomIsNeverPureBlack() {
        for (c in stops(RoomMode.BRIGHT, 0xFF000000.toInt())) assertTrue(BrandColors.luminance(c) >= Palettes.BRIGHT_MIN_FLOOR_LUMINANCE)
    }

    @Test fun roomBackdrop_scrimFollowsStrength() {
        assertEquals(RoomBackdrop.SCRIM_ALPHA, RoomBackdrop.scrimAlpha(0.55f))
        assertTrue(RoomBackdrop.scrimAlpha(0.70f) > RoomBackdrop.scrimAlpha(0.50f))
        assertEquals(0, RoomBackdrop.scrimAlpha(0f))
        assertEquals(255, RoomBackdrop.scrimAlpha(10f))
    }

    private fun colorDistance(a: Int, b: Int): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 255
        return listOf(16, 8, 0).sumOf { Math.abs(ch(a, it) - ch(b, it)) }
    }
}
