package dev.tvlauncher.ui

import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.GlassStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every glass style, on both tiers, keeps body text >= 4.5:1 in every room over white, black and mid backdrops. */
class GlassLooksTest {
    private val backdrops = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(), 0xFFFFFF00.toInt(),
        0xFF4C8DFF.toInt(), 0xFFE50914.toInt())
    private val contents = listOf(null, GlassLooks.dimmedWhite(0.55f), GlassLooks.dimmedWhite(0.7f))

    @Test fun everyStyle_everyRoom_everyBackdrop_textAA() {
        for (style in GlassStyle.values()) for (full in listOf(true, false)) {
            val look = GlassLooks.of(style, full)
            for (m in RoomMode.values()) for (bd in backdrops) for (under in contents) {
                val r = GlassLooks.resolve(look, Palettes.of(m), bd, under)
                val w = GlassLooks.worstUnder(look, bd, under)
                val bg = GlassLooks.textBackground(look, r.rgb, w, r.scrimAlpha)
                for (text in listOf(Theme.TEXT, Theme.TEXT_2)) {
                    val c = Contrast.ratio(text, bg)
                    assertTrue("$style full=$full $m bd=${Integer.toHexString(bd)}: $c", c >= 4.5)
                }
            }
        }
    }

    @Test fun darkBackdrop_staysTranslucent_noTextScrim() {
        val dark = Palettes.of(RoomMode.STANDARD).backdropTop
        val frosted = GlassLooks.resolve(GlassLooks.of(GlassStyle.FROSTED, true), Palettes.of(RoomMode.STANDARD), dark, null)
        assertEquals(0f, frosted.scrimAlpha, 0.001f)
        // Liquid Glass's brighter sheen and lifted sample need a light scrim behind text; the rim band stays clear.
        val liquid = GlassLooks.resolve(GlassLooks.of(GlassStyle.LIQUID, true), Palettes.of(RoomMode.STANDARD), dark, null)
        assertTrue(liquid.scrimAlpha <= 0.6f)
        for (r in listOf(frosted, liquid)) assertTrue((r.tint ushr 24) / 255f in 0.5f..0.73f)
    }

    @Test fun whiteWallpaper_densifiesTheTextScrim() {
        val look = GlassLooks.of(GlassStyle.CLEAR, false)
        val r = GlassLooks.resolve(look, Palettes.of(RoomMode.BRIGHT), 0xFFFFFFFF.toInt(), null)
        assertTrue(r.scrimAlpha > 0.3f)
    }

    @Test fun tokensPerStyle() {
        assertEquals(GlassStyle.LIQUID, GlassStyle.default(true))
        assertEquals(GlassStyle.CLEAR, GlassStyle.default(false))
        assertEquals(GlassStyle.SOLID, GlassStyle.resolve(GlassStyle.SOLID, true))
        val liquid = GlassLooks.of(GlassStyle.LIQUID, true)
        assertTrue(liquid.lens && liquid.chroma && liquid.spring && liquid.sample == GlassLook.Sample.BLUR)
        assertTrue(liquid.tint in 0.5f..0.72f)
        val liteLiquid = GlassLooks.of(GlassStyle.LIQUID, false)
        assertTrue(!liteLiquid.lens && liteLiquid.sample != GlassLook.Sample.BLUR) // no bitmap work on LITE
        val clearLite = GlassLooks.of(GlassStyle.CLEAR, false)
        assertTrue(clearLite.tint in 0.7f..0.8f && clearLite.sample == GlassLook.Sample.NONE && !clearLite.opaque)
        assertTrue(GlassLooks.of(GlassStyle.SOLID, false).opaque)
        assertTrue(GlassLooks.of(GlassStyle.FROSTED, true).opaque)
        for (s in GlassStyle.values()) for (f in listOf(true, false)) {
            val l = GlassLooks.of(s, f)
            assertTrue(l.leak in 0f..1f)
            if (!f) assertTrue("$s LITE has no blur", l.sample != GlassLook.Sample.BLUR)
        }
    }

    @Test fun settingsRoundTrip() {
        val a = AppearanceSettings(glassStyle = GlassStyle.SOLID)
        assertEquals(GlassStyle.SOLID, AppearanceSettings.fromJson(a.toJson()).glassStyle)
        assertEquals(null, AppearanceSettings.fromJson(AppearanceSettings().toJson()).glassStyle)
        assertEquals(null, AppearanceSettings.fromJson("""{"glassStyle":"PLASMA"}""").glassStyle)
    }

    @Test fun colorBoost_saturates_andLifts() {
        val grey = 0xFF808080.toInt()
        assertEquals(grey, ColorBoost.apply(grey, 1.2f, 0f))
        val lifted = ColorBoost.apply(0xFF000000.toInt(), 1f, 0.1f)
        assertTrue((lifted and 0xFF) in 24..27)
        val red = ColorBoost.apply(0xFFC04040.toInt(), 1.2f, 0f)
        assertTrue(((red shr 16) and 0xFF) > 0xC0 && (red and 0xFF) < 0x40)
    }
}

class LensMathTest {
    @Test fun identityInTheCentre_boundedEverywhere_monotonic() {
        for (n in listOf(1, 2, 7, 40, 125, 480)) for (band in listOf(0, 3, 12, n / 4)) {
            val l = LensMath.lut(n, band)
            for (i in 0 until n) assertTrue(l[i] in 0 until n)
            for (i in 1 until n) assertTrue("n=$n band=$band at $i", l[i] >= l[i - 1])
            val b = band.coerceIn(0, n / 2)
            for (i in b until n - b) assertEquals("n=$n centre $i", i, l[i])
        }
    }

    @Test fun edgeMagnifies_towardTheInside() {
        val l = LensMath.lut(100, 12)
        assertTrue(l[0] >= 4) // the edge samples k * band inside
        assertTrue(l[99] <= 95)
        assertEquals(1f, LensMath.source(1f), 0f)
        assertEquals(LensMath.K, LensMath.source(0f), 1e-6f)
        var last = LensMath.source(0f)
        var t = 0.05f
        while (t <= 1f) { val s = LensMath.source(t); assertTrue(s >= last); last = s; t += 0.05f }
    }

    @Test fun remap_isIdentityWithoutBand_andKeepsPixelsOpaque() {
        val w = 10; val h = 6
        val src = IntArray(w * h) { (0xFF shl 24) or (it * 3) }
        val dst = IntArray(w * h)
        LensMath.remap(src, w, h, 0, 0, chroma = false, dst = dst)
        assertTrue(src.contentEquals(dst))
        LensMath.remap(src, w, h, 2, 2, chroma = true, dst = dst)
        assertTrue(dst.all { (it ushr 24) == 0xFF })
    }
}
