package dev.tvlauncher.look

import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import dev.tvlauncher.settings.GlassStyle
import dev.tvlauncher.ui.GlassAmount
import dev.tvlauncher.ui.GlassLooks
import dev.tvlauncher.ui.Theme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LookTest {
    private val backdrops = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF808080.toInt(), 0xFFFFFF00.toInt(),
        0xFF4C8DFF.toInt(), 0xFFE50914.toInt())
    private val contents = listOf(null, GlassLooks.dimmedWhite(0.55f))
    private val amounts = (0..100 step 10).toList()

    @Test fun everyAmount_style_tint_room_backdrop_keepsTextAA() {
        val tints = listOf<Int?>(null, GlassLooks.NEUTRAL_RGB,
            GlassLooks.tintOverride(LookTint.ACCENT, 0xFFFFC94D.toInt()), GlassLooks.tintOverride(LookTint.ACCENT, 0xFFE8ECF2.toInt()))
        for (style in GlassStyle.values()) for (full in listOf(true, false)) for (amount in amounts) {
            val look = GlassLooks.tuned(style, full, amount)
            for (m in RoomMode.values()) for (bd in backdrops) for (under in contents) for (t in tints) {
                val r = GlassLooks.resolve(look, Palettes.of(m), bd, under, t)
                val w = GlassLooks.worstUnder(look, bd, under)
                val bg = GlassLooks.textBackground(look, r.rgb, w, r.scrimAlpha)
                for (text in listOf(Theme.TEXT, Theme.TEXT_2)) {
                    val c = Contrast.ratio(text, bg)
                    assertTrue("$style full=$full a=$amount $m bd=${Integer.toHexString(bd)}: $c", c >= 4.5)
                }
            }
        }
    }

    @Test fun amount_isMonotonic() {
        for (style in GlassStyle.values()) for (full in listOf(true, false)) {
            var prev = GlassLooks.tuned(style, full, 0)
            for (a in 1..100) {
                val l = GlassLooks.tuned(style, full, a)
                assertTrue("$style tint at $a", l.tint <= prev.tint + 1e-6f)
                assertTrue("$style rim at $a", (l.rimLit ushr 24) >= (prev.rimLit ushr 24))
                assertTrue("$style sheen at $a", (l.sheenTop ushr 24) >= (prev.sheenTop ushr 24))
                assertTrue(l.tint >= GlassAmount.MIN_TINT || style == GlassStyle.SOLID)
                prev = l
            }
        }
        for (a in 1..100) assertTrue(GlassAmount.edgeScale(a) >= GlassAmount.edgeScale(a - 1))
    }

    @Test fun neutralAmount_isTheDesignedStyle_andSolidIgnoresAmount() {
        for (s in GlassStyle.values()) for (f in listOf(true, false)) assertEquals(GlassLooks.of(s, f), GlassLooks.tuned(s, f, 60))
        assertEquals(GlassLooks.of(GlassStyle.SOLID, true), GlassLooks.tuned(GlassStyle.SOLID, true, 0))
        assertEquals(GlassLooks.of(GlassStyle.SOLID, true), GlassLooks.tuned(GlassStyle.SOLID, true, 100))
        assertTrue(GlassLooks.tuned(GlassStyle.FROSTED, true, 0).tint >= 0.95f)
        assertFalse(GlassLooks.tuned(GlassStyle.LIQUID, true, 20).lens)
        assertTrue(GlassLooks.tuned(GlassStyle.LIQUID, true, 80).lens)
        // One cached look per key: no allocation when nothing changed.
        assertSame(GlassLooks.tuned(GlassStyle.CLEAR, false, 35), GlassLooks.tuned(GlassStyle.CLEAR, false, 35))
    }

    @Test fun blurStrength_perTier() {
        assertEquals(6, GlassAmount.blurDenominator(0, true))
        assertEquals(16, GlassAmount.blurDenominator(100, true))
        for (a in 1..100) assertTrue(GlassAmount.blurDenominator(a, true) >= GlassAmount.blurDenominator(a - 1, true))
        for (a in amounts) {
            assertEquals(0, GlassAmount.blurDenominator(a, false))
            assertTrue("LITE never blurs", GlassLooks.tuned(GlassStyle.LIQUID, false, a).sample != dev.tvlauncher.ui.GlassLook.Sample.BLUR)
        }
    }

    @Test fun corners_scaleRadii() {
        for (base in listOf(12, 14, 24)) {
            val r = Corners.values().map { it.radiusDp(base) }
            for (i in 1 until r.size) assertTrue(r[i] > r[i - 1])
            assertEquals(base, Corners.ROUND.radiusDp(base))
            assertTrue(r.all { it >= 0 })
        }
    }

    @Test fun codec_roundTrips_andValidates() {
        val l = Look(GlassStyle.CLEAR, 35, Corners.PILL, LookTint.ACCENT, 0xFF22C3D6.toInt(), "L")
        val back = LookCodec.decode(LookCodec.encode(l, "Evening"))
        assertNotNull(back)
        assertEquals("Evening", back!!.name)
        assertEquals(l, back.look)
        val wild = JSONObject().put("kind", LookCodec.KIND).put("version", 1).put("amount", 900).put("corners", "BLOB")
            .put("accent", 0x22C3D6).put("textSize", "HUGE").put("extra", "ignored").put("iconStyle", "CIRCLE").toString()
        val w = LookCodec.decode(wild)!!.look
        assertEquals(100, w.amount)
        assertEquals(Corners.ROUND, w.corners)
        assertEquals(0xFF22C3D6.toInt(), w.accent)
        assertNull(w.textSize)
        assertEquals(0, LookCodec.decode(JSONObject().put("kind", LookCodec.KIND).put("version", 1).put("amount", -5).toString())!!.look.amount)
        assertNull(LookCodec.decode("""{"kind":"h02.look","version":2}"""))
        assertNull(LookCodec.decode("""{"version":1}"""))
        assertNull(LookCodec.decode("not json"))
        assertNull(LookCodec.decode(null))
        assertNull(LookCodec.decode("""{"kind":"h02.look","version":1,"name":"${"x".repeat(9000)}"}"""))
        assertEquals(Look(), LookCodec.currentOf("garbage"))
        assertTrue(LookCodec.encode(l, "x".repeat(200)).length < LookCodec.MAX_BYTES)
        assertFalse("no tile field until tiles read it", LookCodec.encode(l).contains("iconStyle"))
    }

    @Test fun savedLooks_limitAndNames() {
        var list = emptyList<NamedLook>()
        for (i in 1..5) list = LookCodec.save(list, "Look $i", Look(amount = i * 10))!!
        assertNull(LookCodec.save(list, "Sixth", Look()))
        assertEquals(5, LookCodec.save(list, "look 3", Look(amount = 0))!!.size) // same name replaces
        assertNull(LookCodec.save(list, "   ", Look()))
        val back = LookCodec.decodeSaved(LookCodec.encodeSaved(list))
        assertEquals(list, back)
        assertEquals(4, LookCodec.delete(list, "LOOK 1").size)
        assertEquals(emptyList<NamedLook>(), LookCodec.decodeSaved("[1,2,{}]"))
        assertEquals("ab", LookCodec.cleanName(" a\u0000b "))
        assertEquals(LookCodec.MAX_NAME, LookCodec.cleanName("y".repeat(99)).length)
        assertEquals("a_b.json", LookFileName.of("a/b"))
        assertEquals("Look.json", LookFileName.of(""))
    }

    @Test fun presets_areDistinct_andComplete() {
        assertEquals(listOf("Mintaka", "Clear", "Cinema", "Bright room", "Classic"), LookPresets.ALL.map { it.name })
        assertEquals(Look(), LookPresets.MINTAKA.look)
        assertEquals(GlassStyle.SOLID, LookPresets.CLASSIC.look.glassStyle)
        assertTrue(LookPresets.CINEMA.look.amount < Look.DEFAULT_AMOUNT)
        assertEquals(LookPresets.ALL.size, LookPresets.ALL.map { it.look }.toSet().size)
        for (p in LookPresets.ALL) assertEquals(p.look, LookCodec.decode(LookCodec.encode(p.look, p.name))!!.look)
        assertEquals(12, LookPresets.SWATCHES.size)
        assertTrue(LookPresets.SWATCHES.all { (it ushr 24) == 0xFF })
    }

    @Test fun slider_snapsToFivePercent() {
        assertEquals(60, LookCodec.snap(61))
        assertEquals(65, LookCodec.snap(63))
        assertEquals(100, LookCodec.snap(400))
        assertEquals(0, LookCodec.snap(-3))
    }
}
