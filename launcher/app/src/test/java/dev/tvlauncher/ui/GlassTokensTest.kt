package dev.tvlauncher.ui

import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import dev.tvlauncher.settings.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared glass style keeps body text at WCAG AA on every glass surface in every room palette. */
class GlassTokensTest {
    private val modes = RoomMode.values().toList()

    /** Fixed presets, the default, known brands, extremes (white/yellow) and dark brands. */
    private val accents = AppearanceSettings.PRESETS + AppearanceSettings.DEFAULT_ACCENT + listOf(
        0xFFE50914.toInt(), 0xFFFF0000.toInt(), 0xFF00A8E1.toInt(), 0xFF1DB954.toInt(), 0xFFE5A00D.toInt(),
        BrandColors.APPLE_TV, BrandColors.NEUTRAL, 0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt()
    )

    /** Every backdrop the launcher can draw: the room gradient tinted by any accent, plus white (a wallpaper). */
    private fun backdrops(mode: RoomMode): List<Int> {
        val p = Palettes.of(mode)
        val out = ArrayList<Int>()
        val s = IntArray(3)
        for (a in accents) {
            RoomBackdrop.stops(BrandColors.backdropAccent(a), p.backdropTop, p.backdropBottom, p.backdropFloor, p.accentBlend, s)
            out += GlassTokens.brightest(s)
            out += s.toList()
        }
        out += 0xFFFFFFFF.toInt()
        out += 0xFF808080.toInt()
        return out
    }

    @Test
    fun frostedGlass_keepsBodyTextAA_inEveryPalette_overEveryBackdrop() {
        for (m in modes) {
            val p = Palettes.of(m)
            for (bd in backdrops(m)) {
                val tint = GlassTokens.tint(p, bd)
                for (bg in listOf(GlassTokens.surfaceOn(tint, bd), GlassTokens.restingRowOn(tint, bd))) {
                    for (text in listOf(Theme.TEXT, Theme.TEXT_2)) {
                        val c = Contrast.ratio(text, bg)
                        assertTrue("$m bd=${hex(bd)} text=${hex(text)}: ${"%.2f".format(c)}", c >= 4.5)
                    }
                }
                // The selected sidebar entry (a brighter wash) still reads.
                val selected = Contrast.over(GlassTokens.SELECTED, GlassTokens.surfaceOn(tint, bd))
                assertTrue("$m selected", Contrast.ratio(Theme.TEXT, selected) >= 4.5)
            }
        }
    }

    @Test
    fun clearGlass_keepsWhiteTextAA_inEveryPalette() {
        for (m in modes) {
            val p = Palettes.of(m)
            for (bd in backdrops(m)) {
                val bg = GlassTokens.surfaceOn(GlassTokens.clearTint(p, bd), bd)
                val c = Contrast.ratio(Theme.TEXT, bg)
                assertTrue("$m bd=${hex(bd)}: ${"%.2f".format(c)}", c >= 4.5)
            }
        }
    }

    @Test
    fun focusedGlass_darkTextOnLitWhite() {
        for (m in modes) for (bd in backdrops(m)) {
            val lit = Contrast.over(GlassTokens.FOCUS_FILL, GlassTokens.surfaceOn(GlassTokens.tint(Palettes.of(m), bd), bd))
            assertTrue(Contrast.ratio(Theme.TEXT_DARK, lit) >= 7.0)
            assertTrue(Contrast.ratio(Contrast.over(Theme.TEXT_DARK_2, lit), lit) >= 4.5)
        }
    }

    @Test
    fun darkRooms_getRealTranslucency_brightOnesADenserTint() {
        val cinema = Palettes.of(RoomMode.CINEMA)
        val darkBackdrop = cinema.backdropTop
        val a = (GlassTokens.tint(cinema, darkBackdrop) ushr 24) / 255f
        assertEquals(GlassTokens.MIN_TINT_ALPHA, a, 0.01f)
        val white = (GlassTokens.tint(Palettes.of(RoomMode.STANDARD), 0xFFFFFFFF.toInt()) ushr 24) / 255f
        assertTrue(white > a)
        assertTrue(white <= GlassTokens.MAX_TINT_ALPHA + 0.005f)
        // Clear glass is lighter than frosted on the same backdrop.
        val std = Palettes.of(RoomMode.STANDARD)
        assertTrue((GlassTokens.clearTint(std, std.backdropTop) ushr 24) <= (GlassTokens.tint(std, std.backdropTop) ushr 24))
    }

    @Test
    fun glassRgb_keepsThePaletteColour_unlessTextWouldFail() {
        for (m in modes) {
            val p = Palettes.of(m)
            val own = p.glassFill and 0xFFFFFF
            val rgb = GlassTokens.glassRgb(p, p.backdropTop)
            val ownWorks = GlassTokens.worstTextContrast(own, GlassTokens.MAX_TINT_ALPHA, p.backdropTop) >= GlassTokens.TARGET_CONTRAST
            if (ownWorks) assertEquals("$m", own, rgb)
            else assertTrue("$m darkened", Contrast.luminance(rgb) < Contrast.luminance(own))
        }
        // The dark rooms keep their own (warm) glass.
        for (m in listOf(RoomMode.CINEMA, RoomMode.EVENING, RoomMode.STANDARD)) {
            val p = Palettes.of(m)
            assertEquals("$m", p.glassFill and 0xFFFFFF, GlassTokens.glassRgb(p, p.backdropTop))
        }
    }

    @Test
    fun tint_isDeterministic_andMonotoneInBackdropBrightness() {
        val p = Palettes.of(RoomMode.STANDARD)
        var last = 0f
        for (v in 0..255 step 15) {
            val bd = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            val rgb = p.glassFill and 0xFFFFFF
            val a = GlassTokens.tintAlpha(rgb, bd)
            assertTrue("grey $v: $a < $last", a >= last - 0.001f)
            last = a
            assertEquals(a, GlassTokens.tintAlpha(rgb, bd), 0f)
        }
    }

    @Test
    fun brightest_picksTheHighestLuminance() {
        assertEquals(0xFFFFFF00.toInt(), GlassTokens.brightest(intArrayOf(0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt())))
        assertEquals(Theme.BASE, GlassTokens.brightest(IntArray(0)))
    }

    @Test
    fun edge_isOnePixel_upTo1080p() {
        assertEquals(1, GlassTokens.edgePx(1f))
        assertEquals(1, GlassTokens.edgePx(1.33f))
        assertEquals(1, GlassTokens.edgePx(2f))
        assertTrue(GlassTokens.edgePx(4f) in 2..3)
    }

    @Test
    fun sharedRadii() {
        assertTrue(GlassTokens.RADIUS_PANEL > GlassTokens.RADIUS_ITEM)
        assertTrue(GlassTokens.RADIUS_ITEM > GlassTokens.RADIUS_CHIP)
    }

    private fun hex(c: Int) = Integer.toHexString(c)
}

class BoxBlurTest {
    @Test
    fun flatImage_staysFlat() {
        val px = IntArray(40 * 20) { 0xFF336699.toInt() }
        BoxBlur.blur(px, 40, 20, 3)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test
    fun spike_spreads_andKeepsEnergyRoughly() {
        val w = 21; val h = 21
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        px[10 * w + 10] = 0xFFFFFFFF.toInt()
        BoxBlur.blur(px, w, h, 2, passes = 1)
        val center = px[10 * w + 10] and 0xFF
        val near = px[10 * w + 11] and 0xFF
        val far = px[0] and 0xFF
        assertTrue(center in 1..254)
        assertTrue(near > 0)
        assertEquals(0, far)
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun edgeCases_doNotThrow() {
        BoxBlur.blur(IntArray(0), 0, 0, 3)
        BoxBlur.blur(IntArray(1) { -1 }, 1, 1, 3)
        BoxBlur.blur(IntArray(4), 4, 1, 0)
        BoxBlur.blur(IntArray(3), 4, 1, 2) // too small: ignored
    }

    @Test
    fun copyIsOneEighth_andSmall() {
        assertEquals(160 to 90, BoxBlur.scaled(1280, 720))
        assertEquals(1 to 1, BoxBlur.scaled(3, 3))
        assertTrue(BoxBlur.bytes(1280, 720) < 64 * 1024)
        assertTrue(BoxBlur.bytes(3840, 2160) <= 520 * 1024)
    }
}
