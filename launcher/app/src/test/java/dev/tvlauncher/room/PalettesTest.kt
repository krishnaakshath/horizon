package dev.tvlauncher.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {
    private val modes = RoomMode.values().toList()

    @Test fun contrast_helper_known_values() {
        assertEquals(21.0, Contrast.ratio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777.toInt(), 0xFF777777.toInt()), 1e-9)
        // Symmetric.
        assertEquals(Contrast.ratio(0xFF336699.toInt(), 0xFFEEEEEE.toInt()), Contrast.ratio(0xFFEEEEEE.toInt(), 0xFF336699.toInt()), 1e-12)
        // #767676 on white is the classic 4.54:1.
        assertEquals(4.54, Contrast.ratio(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(0.0, Contrast.luminance(0xFF000000.toInt()), 1e-12)
        assertEquals(1.0, Contrast.luminance(0xFFFFFFFF.toInt()), 1e-12)
    }

    @Test fun composite_over() {
        assertEquals(0xFF000000.toInt(), Contrast.over(0x00FFFFFF, 0xFF000000.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), Contrast.over(0xFFFFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(0xFF808080.toInt(), Contrast.over(0x80FFFFFF.toInt(), 0xFF000000.toInt()))
    }

    @Test fun text_primary_is_aaa_on_every_backdrop_and_surface() {
        for (m in modes) {
            val p = Palettes.of(m)
            for (bg in listOf(p.backdropFloor, p.backdropTop, p.backdropBottom, p.surface, p.surfaceElevated)) {
                val r = Contrast.ratio(p.textPrimary, bg)
                assertTrue("$m textPrimary ${"%.2f".format(r)}", r >= 7.0)
            }
        }
    }

    @Test fun text_secondary_is_aa_on_backdrops_and_surface() {
        for (m in modes) {
            val p = Palettes.of(m)
            for (bg in listOf(p.backdropFloor, p.backdropTop, p.surface)) {
                val r = Contrast.ratio(p.textSecondary, bg)
                assertTrue("$m textSecondary ${"%.2f".format(r)}", r >= 4.5)
            }
        }
    }

    @Test fun focus_ring_visible_on_surface() {
        for (m in modes) {
            val p = Palettes.of(m)
            assertTrue("$m ring", Contrast.ratio(p.focusRingColor, p.surface) >= 3.0)
            assertTrue("$m ring on backdrop", Contrast.ratio(p.focusRingColor, p.backdropTop) >= 3.0)
        }
    }

    @Test fun glass_text_legible() {
        for (m in modes) {
            val p = Palettes.of(m)
            val glass = Contrast.over(p.glassFill, p.backdropTop)
            val r = Contrast.ratio(p.textPrimary, glass)
            assertTrue("$m glass ${"%.2f".format(r)}", r >= 7.0)
            // And over a worst case: the glass sitting on a bright brand-tinted area of the backdrop.
            val worst = Contrast.over(p.glassFill, 0xFF6A7A90.toInt())
            assertTrue("$m glass over tinted", Contrast.ratio(p.textPrimary, worst) >= 4.5)
        }
    }

    @Test fun modes_strictly_ordered_by_black_floor() {
        val l = modes.map { Contrast.luminance(Palettes.of(it).backdropFloor) }
        for (i in 1 until l.size) assertTrue("floor order at $i: $l", l[i] > l[i - 1])
    }

    @Test fun successive_black_floors_differ_at_least_2x() {
        val l = modes.map { Contrast.luminance(Palettes.of(it).backdropFloor) }
        for (i in 1 until l.size) assertTrue("floor ratio at $i: $l", l[i] / l[i - 1] >= Palettes.MIN_FLOOR_STEP)
        assertEquals(2.0, Palettes.MIN_FLOOR_STEP, 0.0)
    }

    @Test fun floor_luminances_are_the_documented_values() {
        // Hand-computed (WCAG: c/12.92 if c <= 0.04045 else ((c + 0.055) / 1.055)^2.4, c = v / 255):
        //  CINEMA #040404: 4/255 = 0.015686 -> /12.92 = 0.0012141; grey, so L = 0.00121.
        //  BRIGHT #343B49: R 52 -> 0.03434, G 59 -> 0.04374, B 73 -> 0.06663;
        //    L = 0.2126*0.03434 + 0.7152*0.04374 + 0.0722*0.06663 = 0.00730 + 0.03128 + 0.00481 = 0.04339.
        assertEquals(0.00121, Contrast.luminance(Palettes.of(RoomMode.CINEMA).backdropFloor), 0.00001)
        assertEquals(0.00414, Contrast.luminance(Palettes.of(RoomMode.EVENING).backdropFloor), 0.00001)
        assertEquals(0.01082, Contrast.luminance(Palettes.of(RoomMode.STANDARD).backdropFloor), 0.00001)
        assertEquals(0.04339, Contrast.luminance(Palettes.of(RoomMode.BRIGHT).backdropFloor), 0.00001)
        // BRIGHT surface ~L 0.07 (beats a ~3 % reflection of a lit room).
        assertEquals(0.0696, Contrast.luminance(Palettes.of(RoomMode.BRIGHT).surface), 0.0005)
    }

    @Test fun evening_is_visibly_warmer_than_cinema() {
        fun warmth(c: Int) = ((c shr 16) and 255) - (c and 255)
        val c = Palettes.of(RoomMode.CINEMA); val e = Palettes.of(RoomMode.EVENING)
        // Backdrop top: R-B at least MIN_EVENING_WARMTH_SHIFT (6) channel steps warmer.
        assertTrue(warmth(e.backdropTop) - warmth(c.backdropTop) >= Palettes.MIN_EVENING_WARMTH_SHIFT)
        // Text: at least 10 steps warmer.
        assertTrue(warmth(e.textPrimary) - warmth(c.textPrimary) >= 10)
        // And EVENING's top is visibly lighter (>= 2x luminance).
        assertTrue(Contrast.luminance(e.backdropTop) / Contrast.luminance(c.backdropTop) >= 2.0)
    }

    @Test fun glass_opacity_bounds() {
        assertTrue(alpha(Palettes.of(RoomMode.BRIGHT).glassFill) >= 0xE0)
        assertTrue(alpha(Palettes.of(RoomMode.CINEMA).glassFill) <= 0xC8)
        val a = modes.map { alpha(Palettes.of(it).glassFill) }
        for (i in 1 until a.size) assertTrue(a[i] >= a[i - 1])
    }

    @Test fun blender_matches_blend_exactly() {
        val b = PaletteBlender()
        val f = PaletteFrame()
        for (x in modes) for (y in modes) {
            b.prepare(Palettes.of(x), Palettes.of(y))
            for (i in 0..20) {
                val t = i / 20f
                b.blendInto(t, f)
                assertEquals("$x->$y @$t", Palettes.blend(Palettes.of(x), Palettes.of(y), t), f.toPalette())
            }
        }
    }

    @Test fun focus_ring_width_non_decreasing_and_bright_is_thick() {
        val w = modes.map { Palettes.of(it).focusRingWidthDp }
        for (i in 1 until w.size) assertTrue(w[i] >= w[i - 1])
        assertEquals(3f, Palettes.of(RoomMode.STANDARD).focusRingWidthDp, 0f)
        assertEquals(4f, Palettes.of(RoomMode.BRIGHT).focusRingWidthDp, 0f)
    }

    @Test fun bright_never_pure_black() {
        val p = Palettes.of(RoomMode.BRIGHT)
        for (c in listOf(p.backdropFloor, p.backdropTop, p.backdropBottom, p.surface)) {
            assertTrue(Contrast.luminance(c) >= Palettes.BRIGHT_MIN_FLOOR_LUMINANCE)
        }
        assertTrue(Palettes.BRIGHT_MIN_FLOOR_LUMINANCE >= 0.04)
        assertEquals(0xFFFFFFFF.toInt(), p.textPrimary)
        // Less translucency than the dark modes.
        assertTrue(alpha(p.glassFill) > alpha(Palettes.of(RoomMode.CINEMA).glassFill))
        assertTrue(p.glowStrength < Palettes.of(RoomMode.STANDARD).glowStrength)
        assertTrue(p.minTextScale > 1f)
        assertTrue(p.textWeightBoost > 0.5f)
    }

    @Test fun cinema_caps_white_level_and_softens_glow() {
        val c = Palettes.of(RoomMode.CINEMA)
        val maxChannel = maxOf((c.textPrimary shr 16) and 255, (c.textPrimary shr 8) and 255, c.textPrimary and 255)
        assertTrue(maxChannel <= 0xD6)
        // Warm-neutral: red >= blue.
        assertTrue(((c.textPrimary shr 16) and 255) >= (c.textPrimary and 255))
        assertTrue(c.glowStrength < Palettes.of(RoomMode.STANDARD).glowStrength)
        assertTrue(Contrast.luminance(c.backdropBottom) < 0.003)
    }

    @Test fun fractions_in_range() {
        for (m in modes) {
            val p = Palettes.of(m)
            for (f in listOf(p.accentBlend, p.glowStrength, p.shadowStrength, p.tileDimAlpha, p.textWeightBoost, p.scrimStrength)) {
                assertTrue("$m $f", f in 0f..1f)
            }
            assertTrue(p.minTextScale in 1f..1.5f)
        }
    }

    @Test fun blend_endpoints_equal_inputs() {
        for (a in modes) for (b in modes) {
            assertEquals(Palettes.of(a), Palettes.blend(Palettes.of(a), Palettes.of(b), 0f))
            assertEquals(Palettes.of(b), Palettes.blend(Palettes.of(a), Palettes.of(b), 1f))
        }
    }

    @Test fun blend_clamps_t_and_handles_nan() {
        val a = Palettes.of(RoomMode.CINEMA); val b = Palettes.of(RoomMode.BRIGHT)
        assertEquals(a, Palettes.blend(a, b, -3f))
        assertEquals(b, Palettes.blend(a, b, 7f))
        assertEquals(a, Palettes.blend(a, b, Float.NaN))
    }

    @Test fun blend_deterministic_and_no_nan() {
        val a = Palettes.of(RoomMode.CINEMA); val b = Palettes.of(RoomMode.BRIGHT)
        for (i in 0..100) {
            val t = i / 100f
            val p = Palettes.blend(a, b, t)
            assertEquals(p, Palettes.blend(a, b, t))
            for (f in floats(p)) assertFalse(f.isNaN())
        }
    }

    @Test fun blend_is_monotone_per_channel() {
        for (a in modes) for (b in modes) {
            val pa = Palettes.of(a); val pb = Palettes.of(b)
            var prev = pa
            for (i in 1..50) {
                val cur = Palettes.blend(pa, pb, i / 50f)
                colors(prev).zip(colors(cur)).zip(colors(pb)).forEachIndexed { idx, (pc, target) ->
                    val (p0, c1) = pc
                    for (s in intArrayOf(24, 16, 8, 0)) {
                        val x0 = (p0 shr s) and 255; val x1 = (c1 shr s) and 255; val xt = (target shr s) and 255
                        val start = (colors(pa)[idx] shr s) and 255
                        if (xt >= start) assertTrue("$a->$b field $idx shift $s", x1 >= x0) else assertTrue(x1 <= x0)
                    }
                }
                floats(prev).zip(floats(cur)).zip(floats(pb)).forEachIndexed { idx, (pc, target) ->
                    val start = floats(pa)[idx]
                    if (target >= start) assertTrue(pc.second >= pc.first - 1e-6f) else assertTrue(pc.second <= pc.first + 1e-6f)
                }
                prev = cur
            }
        }
    }

    @Test fun blend_midpoint_is_between_in_luminance() {
        val a = Palettes.of(RoomMode.CINEMA); val b = Palettes.of(RoomMode.BRIGHT)
        val m = Palettes.blend(a, b, 0.5f)
        val la = Contrast.luminance(a.backdropFloor); val lb = Contrast.luminance(b.backdropFloor); val lm = Contrast.luminance(m.backdropFloor)
        assertTrue(lm > la && lm < lb)
        // Linear-light blend: the midpoint luminance is close to the mean of the endpoints.
        assertEquals((la + lb) / 2, lm, 0.002)
    }

    @Test fun frame_matches_blend() {
        val a = Palettes.of(RoomMode.EVENING); val b = Palettes.of(RoomMode.STANDARD)
        val f = PaletteFrame()
        for (t in listOf(0f, 0.25f, 0.5f, 1f)) {
            Palettes.blendInto(a, b, t, f)
            assertEquals(Palettes.blend(a, b, t), f.toPalette())
        }
    }

    private fun alpha(c: Int) = (c ushr 24) and 255
    private fun colors(p: Palette) = listOf(p.backdropFloor, p.backdropTop, p.backdropBottom, p.surface, p.surfaceElevated,
        p.glassFill, p.glassBorder, p.textPrimary, p.textSecondary, p.textDisabled, p.focusRingColor)
    private fun floats(p: Palette) = listOf(p.accentBlend, p.glowStrength, p.shadowStrength, p.focusRingWidthDp,
        p.tileDimAlpha, p.textWeightBoost, p.minTextScale, p.scrimStrength)
}
