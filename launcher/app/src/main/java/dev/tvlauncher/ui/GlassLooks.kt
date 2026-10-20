package dev.tvlauncher.ui

import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palette
import dev.tvlauncher.settings.GlassStyle

/**
 * One [GlassStyle] on one device tier (FULL: blur, lens and shadows; LITE: no bitmap work), as
 * pure tokens. Every glass surface is drawn in this order (GlassDrawable):
 *
 * 1. [sample]: the backdrop behind the card. BLUR = the pre-blurred 1/8-scale copy (FULL; Liquid
 *    Glass adds the [lens] remap of its outer band), GRADIENT = the backdrop's own gradient in
 *    window coordinates (what a blur of a smooth gradient looks like), NONE = nothing (the card
 *    is truly translucent). [sampleAlpha] < 1 lets dimmed content behind show through (Clear).
 *    The sample gets a [saturation] boost and a [lift] in brightness (baked, never per frame).
 * 2. The tint: the room's glass colour at [tint] alpha (the visible "glass").
 * 3. The text scrim: an extra layer of the same colour over the content area (the card minus a
 *    rim band) whose alpha is computed per backdrop ([GlassLooks.textScrim]) so body text keeps
 *    >= 4.5:1 over ANY backdrop. Over a dark backdrop it is 0; over a white wallpaper it densifies.
 * 4. [sheenTop]: a top-left lit sheen; 5. [innerGlow]: a soft glow just inside the edge;
 * 6. the specular rim: [rimLit] on the lit (top-left) edge fading to [rimShade] opposite, plus a
 *    faint darker [outerEdge]; 7. [chroma]: a whisper of colour fringing on the rim (Liquid Glass).
 * FULL adds a soft [shadow]; [spring] makes shapes and focus move with a little overshoot.
 */
data class GlassLook(
    val style: GlassStyle,
    val full: Boolean,
    val sample: Sample,
    val sampleAlpha: Float,
    val lens: Boolean,
    val tint: Float,
    val saturation: Float,
    val lift: Float,
    val sheenTop: Int,
    val sheenFraction: Float,
    val rimLit: Int,
    val rimShade: Int,
    val outerEdge: Int,
    val innerGlow: Int,
    val glowDp: Int,
    val chroma: Boolean,
    val shadow: Boolean,
    val spring: Boolean
) {
    enum class Sample { NONE, GRADIENT, BLUR }

    /** True when nothing behind the card can show through (sample or tint fully covers it). */
    val opaque: Boolean get() = (sample != Sample.NONE && sampleAlpha >= 1f) || tint >= 0.95f

    /** Fraction of whatever is behind that still shows through the sample and tint (before the text scrim). */
    val leak: Float get() = (1f - (if (sample == Sample.NONE) 0f else sampleAlpha)) * (1f - tint)
}

object GlassLooks {
    /** Rim band (fraction of the card's smaller side) left outside the text scrim, and its floor (dp). */
    const val BAND_FRACTION = 0.12f
    const val BAND_MIN_DP = 6
    /** Solid style: the shared panel surface colour (PanelStyle.SURFACE RGB). */
    const val SOLID_RGB = 0x151922

    fun of(style: GlassStyle, full: Boolean): GlassLook = when (style) {
        GlassStyle.LIQUID -> if (full) GlassLook(style, true, GlassLook.Sample.BLUR, 1f, lens = true, tint = 0.52f,
            saturation = 1.18f, lift = 0.06f, sheenTop = 0x1AFFFFFF, sheenFraction = 0.5f,
            rimLit = 0x80FFFFFF.toInt(), rimShade = 0x1AFFFFFF, outerEdge = 0x38000000, innerGlow = 0x1FFFFFFF, glowDp = 6,
            chroma = true, shadow = true, spring = true)
        else GlassLook(style, false, GlassLook.Sample.GRADIENT, 1f, lens = false, tint = 0.62f,
            saturation = 1.10f, lift = 0.04f, sheenTop = 0x16FFFFFF, sheenFraction = 0.5f,
            rimLit = 0x66FFFFFF, rimShade = 0x14FFFFFF, outerEdge = 0x30000000, innerGlow = 0x14FFFFFF, glowDp = 4,
            chroma = false, shadow = false, spring = true)
        GlassStyle.FROSTED -> if (full) GlassLook(style, true, GlassLook.Sample.BLUR, 1f, lens = false, tint = 0.62f,
            saturation = 1.12f, lift = 0.04f, sheenTop = 0x10FFFFFF, sheenFraction = 0.42f,
            rimLit = 0x3DFFFFFF, rimShade = 0x0AFFFFFF, outerEdge = 0x26000000, innerGlow = 0, glowDp = 0,
            chroma = false, shadow = true, spring = false)
        else GlassLook(style, false, GlassLook.Sample.GRADIENT, 1f, lens = false, tint = 0.72f,
            saturation = 1.06f, lift = 0.02f, sheenTop = 0x0EFFFFFF, sheenFraction = 0.42f,
            rimLit = 0x33FFFFFF, rimShade = 0x08FFFFFF, outerEdge = 0x1F000000, innerGlow = 0, glowDp = 0,
            chroma = false, shadow = false, spring = false)
        GlassStyle.CLEAR -> if (full) GlassLook(style, true, GlassLook.Sample.BLUR, 0.55f, lens = false, tint = 0.45f,
            saturation = 1.15f, lift = 0.05f, sheenTop = 0x12FFFFFF, sheenFraction = 0.45f,
            rimLit = 0x4DFFFFFF, rimShade = 0x10FFFFFF, outerEdge = 0x26000000, innerGlow = 0x0FFFFFFF, glowDp = 4,
            chroma = false, shadow = true, spring = false)
        else GlassLook(style, false, GlassLook.Sample.NONE, 0f, lens = false, tint = 0.75f,
            saturation = 1f, lift = 0f, sheenTop = 0x10FFFFFF, sheenFraction = 0.45f,
            rimLit = 0x40FFFFFF, rimShade = 0x0CFFFFFF, outerEdge = 0x26000000, innerGlow = 0, glowDp = 0,
            chroma = false, shadow = false, spring = false)
        GlassStyle.SOLID -> GlassLook(style, full, GlassLook.Sample.NONE, 0f, lens = false, tint = 0.96f,
            saturation = 1f, lift = 0f, sheenTop = 0x08FFFFFF, sheenFraction = 0.42f,
            rimLit = 0x14FFFFFF, rimShade = 0x05FFFFFF, outerEdge = 0, innerGlow = 0, glowDp = 0,
            chroma = false, shadow = full, spring = false)
    }

    private val tunedCache = HashMap<Int, GlassLook>()

    /** [of] adjusted by the glass [amount] ([GlassAmount]); one cached look per (style, tier, amount). */
    fun tuned(style: GlassStyle, full: Boolean, amount: Int): GlassLook {
        val a = amount.coerceIn(0, 100)
        val key = (style.ordinal shl 8) or (if (full) 0x80 else 0) or a
        return synchronized(tunedCache) { tunedCache.getOrPut(key) { GlassAmount.apply(of(style, full), a) } }
    }

    /** The tint colour (RGB) of [look] in [palette] (Solid uses the panel surface). */
    fun tintRgb(look: GlassLook, palette: Palette?, worst: Int): Int =
        if (look.style == GlassStyle.SOLID) SOLID_RGB else GlassTokens.glassRgb(palette, worst)

    /**
     * The worst (brightest) colour directly under the glass: the boosted backdrop sample when it
     * covers fully; with a translucent card also what is behind it, i.e. [contentUnder] (null:
     * only the backdrop is behind, e.g. pills; else the scrim-dimmed content of a panel).
     */
    fun worstUnder(look: GlassLook, backdropBrightest: Int, contentUnder: Int?): Int {
        val boosted = ColorBoost.apply(backdropBrightest or (0xFF shl 24), look.saturation, look.lift)
        if (look.sample != GlassLook.Sample.NONE && look.sampleAlpha >= 1f) return boosted
        val behind = contentUnder ?: (backdropBrightest or (0xFF shl 24))
        return GlassTokens.brightest(intArrayOf(boosted, behind))
    }

    /** What content behind a panel looks like at worst: white under a scrim of [scrimAlpha] (0 = no scrim: white). */
    fun dimmedWhite(scrimAlpha: Float): Int =
        Contrast.over(GlassTokens.withAlpha(0x000000, scrimAlpha), 0xFFFFFFFF.toInt())

    /** The colour under text: [under], the tint, the text scrim at [scrim], the sheen and the brightest wash. */
    fun textBackground(look: GlassLook, rgb: Int, under: Int, scrim: Float): Int {
        var c = Contrast.over(GlassTokens.withAlpha(rgb, look.tint), under or (0xFF shl 24))
        if (scrim > 0f) c = Contrast.over(GlassTokens.withAlpha(rgb, scrim), c)
        c = Contrast.over(look.sheenTop, c)
        return Contrast.over(GlassTokens.WORST_WASH, c)
    }

    fun worstContrast(look: GlassLook, rgb: Int, under: Int, scrim: Float): Double {
        val bg = textBackground(look, rgb, under, scrim)
        return minOf(Contrast.ratio(Theme.TEXT_2, bg), Contrast.ratio(Theme.TEXT, bg))
    }

    /**
     * The text scrim (0..[GlassTokens.MAX_TINT_ALPHA], 2 decimals) for [look] over [under]: the
     * smallest that keeps [Theme.TEXT_2] and [Theme.TEXT] at [GlassTokens.TARGET_CONTRAST].
     */
    fun textScrim(look: GlassLook, rgb: Int, under: Int): Float {
        var s = 0f
        while (s < GlassTokens.MAX_TINT_ALPHA) {
            if (worstContrast(look, rgb, under, s) >= GlassTokens.TARGET_CONTRAST) return s
            s = Math.round((s + 0.01f) * 100f) / 100f
        }
        return GlassTokens.MAX_TINT_ALPHA
    }

    /** Everything a drawable needs for one backdrop: tint colour, tint alpha and text scrim. */
    data class Resolved(val rgb: Int, val tint: Int, val scrim: Int, val scrimAlpha: Float)

    /**
     * The glass colour chosen in Look > Tint (RGB), or null for the backdrop/room colour: Accent is
     * the accent sunk deep toward the base, Neutral a cool dark grey. [resolve] still falls back to
     * the base colour whenever a tint could not keep text readable.
     */
    fun tintOverride(source: dev.tvlauncher.look.LookTint, accent: Int): Int? = when (source) {
        dev.tvlauncher.look.LookTint.BACKDROP -> null
        dev.tvlauncher.look.LookTint.NEUTRAL -> NEUTRAL_RGB
        dev.tvlauncher.look.LookTint.ACCENT -> BrandColors.blend(accent or (0xFF shl 24), Theme.BASE, ACCENT_SINK) and 0xFFFFFF
    }

    const val NEUTRAL_RGB = 0x1C2028
    const val ACCENT_SINK = 0.72f

    fun resolve(look: GlassLook, palette: Palette?, backdropBrightest: Int, contentUnder: Int?, tintRgb: Int? = null): Resolved {
        val under = worstUnder(look, backdropBrightest, contentUnder)
        var rgb = if (tintRgb != null && look.style != GlassStyle.SOLID) tintRgb and 0xFFFFFF else tintRgb(look, palette, under)
        var s = textScrim(look, rgb, under)
        if (worstContrast(look, rgb, under, s) < GlassTokens.TARGET_CONTRAST) {
            rgb = Theme.BASE and 0xFFFFFF
            s = textScrim(look, rgb, under)
        }
        // Hard floor (thin glass from a high Glass amount): the text scrim keeps densifying past
        // its usual cap until text is readable again.
        while (s < 1f && worstContrast(look, rgb, under, s) < GlassTokens.TARGET_CONTRAST) s = minOf(1f, Math.round((s + 0.02f) * 100f) / 100f)
        return Resolved(rgb, GlassTokens.withAlpha(rgb, look.tint), GlassTokens.withAlpha(rgb, s), s)
    }

    /** Rim band in px for a card of [w] x [h] at [density]. */
    fun bandPx(w: Int, h: Int, density: Float): Int =
        maxOf(Math.round(BAND_MIN_DP * density), Math.round(minOf(w, h) * BAND_FRACTION))
}

/**
 * Settings > Appearance > Look > Glass amount (0..100 %, default [NEUTRAL] = the style as designed)
 * as a pure adjustment of a [GlassLook]. Less glass: the tint thickens toward solid, rim, sheen and
 * glow fade and the lens switches off; more glass: the tint thins (never below [MIN_TINT]) and rim
 * and sheen brighten. Every token moves monotonically with the amount. Solid ignores the amount.
 * Text stays readable at every amount: [GlassLooks.resolve] grows the text scrim as the tint thins.
 */
object GlassAmount {
    const val NEUTRAL = 60
    const val MIN_TINT = 0.30f
    const val SOLID_TINT = 0.96f
    /** Below this the Liquid Glass lens and chroma fringe are off. */
    const val LENS_MIN = 40
    /** Rim/sheen scale at 0 and at 100 %. */
    const val EDGE_MIN = 0.35f
    const val EDGE_MAX = 1.4f

    fun apply(look: GlassLook, amount: Int): GlassLook {
        val a = amount.coerceIn(0, 100)
        if (look.style == GlassStyle.SOLID || a == NEUTRAL) return look
        val tint = tint(look.tint, a)
        val e = edgeScale(a)
        return look.copy(
            tint = tint,
            // The sheen lies under text: it never brightens past the designed style.
            sheenTop = scaleAlpha(look.sheenTop, minOf(1f, e)),
            rimLit = scaleAlpha(look.rimLit, e),
            rimShade = scaleAlpha(look.rimShade, e),
            innerGlow = scaleAlpha(look.innerGlow, e),
            lens = look.lens && a >= LENS_MIN,
            chroma = look.chroma && a >= LENS_MIN
        )
    }

    /** The tint alpha of a style whose designed tint is [base], at [amount]: non-increasing in amount. */
    fun tint(base: Float, amount: Int): Float {
        val a = amount.coerceIn(0, 100)
        return if (a <= NEUTRAL) base + (maxOf(SOLID_TINT, base) - base) * (NEUTRAL - a) / NEUTRAL.toFloat()
        else {
            val floor = minOf(base, maxOf(MIN_TINT, base * 0.6f))
            base - (base - floor) * (a - NEUTRAL) / (100f - NEUTRAL)
        }
    }

    /** Rim and sheen multiplier: [EDGE_MIN] at 0, 1 at [NEUTRAL], [EDGE_MAX] at 100; non-decreasing. */
    fun edgeScale(amount: Int): Float {
        val a = amount.coerceIn(0, 100)
        return if (a <= NEUTRAL) EDGE_MIN + (1f - EDGE_MIN) * a / NEUTRAL.toFloat()
        else 1f + (EDGE_MAX - 1f) * (a - NEUTRAL) / (100f - NEUTRAL)
    }

    /**
     * Blur strength as the backdrop sample's scale denominator (FULL: 6 = 1/6 at 0 % .. 16 = 1/16 at
     * 100 %, a smaller copy blurs more); 0 on LITE, which never blurs (gradient translucency only).
     */
    fun blurDenominator(amount: Int, full: Boolean): Int =
        if (!full) 0 else 6 + Math.round(10f * amount.coerceIn(0, 100) / 100f)

    /** [c] with its alpha multiplied by [k] (clamped to 0..255). */
    fun scaleAlpha(c: Int, k: Float): Int {
        val a = Math.round(((c ushr 24) and 0xFF) * k).coerceIn(0, 255)
        return (a shl 24) or (c and 0xFFFFFF)
    }
}

/** Saturation boost and brightness lift on opaque ARGB colours (pure; baked into samples off the main thread). */
object ColorBoost {
    /** [c] with saturation scaled by [sat] around its luma and lifted [lift] toward white. */
    fun apply(c: Int, sat: Float, lift: Float): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        fun ch(v: Int): Int {
            val s = y + (v - y) * sat
            return Math.round(s + (255f - s) * lift).coerceIn(0, 255)
        }
        return (c and 0xFF000000.toInt()) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** In place over [px] (count [n]); returns the brightest result. */
    fun applyAll(px: IntArray, n: Int, sat: Float, lift: Float): Int {
        if (sat == 1f && lift == 0f) return GlassTokens.brightest(px, n)
        for (i in 0 until n) px[i] = apply(px[i], sat, lift)
        return GlassTokens.brightest(px, n)
    }
}

/**
 * The Liquid Glass "lens": a barrel magnification of the outer band of a card, as a pure remap.
 * For an output position at distance d from the nearest edge (t = d / band, t in [0, 1)), the
 * source is taken at t' = t + k (1 - t)^2: the edge itself samples k * band inside, so the band
 * shows the backdrop magnified toward the rim (1 / (1 - 2k) at the edge), and from t >= 1 inward
 * it is the identity. dt'/dt = 1 - 2k (1 - t) > 0 for k < 0.5: monotonic, and t' stays in [k, 1]:
 * bounded inside the card. Chromatic fringing samples red/blue with k +- [CHROMA].
 */
object LensMath {
    const val K = 0.42f
    const val CHROMA = 0.05f

    /** Source distance (in band units) for output distance [t] (band units). */
    fun source(t: Float, k: Float = K): Float {
        if (t >= 1f) return t
        val x = t.coerceAtLeast(0f)
        val u = 1f - x
        return x + k * u * u
    }

    /**
     * Per output index (0 until [n]) the source index, magnifying both ends within [bandPx].
     * Identity in the centre; monotonic non-decreasing; every value in 0 until n.
     */
    fun lut(n: Int, bandPx: Int, k: Float = K, out: IntArray = IntArray(maxOf(0, n))): IntArray {
        if (n <= 0) return out
        val band = bandPx.coerceIn(0, n / 2).toFloat()
        for (i in 0 until n) {
            val c = i + 0.5f
            val left = c <= n / 2f
            val d = if (left) c else n - c
            val src = if (band <= 0f || d >= band) c else {
                val s = band * source(d / band, k)
                if (left) s else n - s
            }
            out[i] = (src - 0.5f).let { Math.round(it) }.coerceIn(0, n - 1)
        }
        // Rounding near the centre seam can step back by one: keep it monotonic.
        for (i in 1 until n) if (out[i] < out[i - 1]) out[i] = out[i - 1]
        return out
    }

    /**
     * Remaps a [w] x [h] crop [src] (opaque ARGB, row-major) into [dst] through per-axis lookup
     * tables; red/green/blue use their own tables when [chroma] (the edge fringe). Pure.
     */
    fun remap(src: IntArray, w: Int, h: Int, bandX: Int, bandY: Int, chroma: Boolean, dst: IntArray) {
        if (w <= 0 || h <= 0) return
        remap(src, Table(w, h, bandX, bandY, chroma), dst)
    }

    /** The precomputed displacement table of one crop size (built once, reused while the size holds). */
    class Table(val w: Int, val h: Int, val bandX: Int, val bandY: Int, val chroma: Boolean) {
        val gx = lut(w, bandX); val gy = lut(h, bandY)
        val rx = if (chroma) lut(w, bandX, K + CHROMA) else gx
        val ry = if (chroma) lut(h, bandY, K + CHROMA) else gy
        val bx = if (chroma) lut(w, bandX, K - CHROMA) else gx
        val by = if (chroma) lut(h, bandY, K - CHROMA) else gy

        fun matches(w: Int, h: Int, bandX: Int, bandY: Int, chroma: Boolean) =
            this.w == w && this.h == h && this.bandX == bandX && this.bandY == bandY && this.chroma == chroma
    }

    /** Remaps [src] (t.w x t.h) into [dst] through [t]. Pure; no allocation. */
    fun remap(src: IntArray, t: Table, dst: IntArray) {
        val w = t.w; val h = t.h; val chroma = t.chroma
        if (w <= 0 || h <= 0) return
        val gx = t.gx; val gy = t.gy; val rx = t.rx; val ry = t.ry; val bx = t.bx; val by = t.by
        for (y in 0 until h) for (x in 0 until w) {
            val g = src[gy[y] * w + gx[x]]
            dst[y * w + x] = if (!chroma) g else {
                val r = src[ry[y] * w + rx[x]]
                val b = src[by[y] * w + bx[x]]
                (0xFF shl 24) or (r and 0xFF0000) or (g and 0xFF00) or (b and 0xFF)
            }
        }
    }
}

/**
 * Where a Liquid Glass panel's lens bitmap comes from (pure): the panel's window rect mapped into
 * the blurred backdrop sample (floor/ceil so the crop always covers the panel, clamped to the
 * sample), with the rim band in sample pixels. Out = [x, y, w, h, bandX, bandY] (no allocation).
 */
object LensPlan {
    /** Distinct panel sizes kept at once (about 30-80 KB each at 1080p, 1/8 scale). */
    const val MAX_ENTRIES = 4
    /** Smaller crops (in sample px) are not worth a lens: the plain sample is drawn. */
    const val MIN_PX = 4
    const val X = 0; const val Y = 1; const val W = 2; const val H = 3; const val BAND_X = 4; const val BAND_Y = 5

    fun crop(left: Int, top: Int, w: Int, h: Int, bandPx: Int, sampleW: Int, sampleH: Int,
             backdropW: Int, backdropH: Int, out: IntArray): Boolean {
        if (w <= 0 || h <= 0 || sampleW <= 0 || sampleH <= 0 || backdropW <= 0 || backdropH <= 0) return false
        val sx = sampleW.toFloat() / backdropW
        val sy = sampleH.toFloat() / backdropH
        val x0 = Math.floor((left * sx).toDouble()).toInt().coerceIn(0, sampleW - 1)
        val y0 = Math.floor((top * sy).toDouble()).toInt().coerceIn(0, sampleH - 1)
        val x1 = Math.ceil(((left + w) * sx).toDouble()).toInt().coerceIn(x0 + 1, sampleW)
        val y1 = Math.ceil(((top + h) * sy).toDouble()).toInt().coerceIn(y0 + 1, sampleH)
        val cw = x1 - x0
        val ch = y1 - y0
        if (cw < MIN_PX || ch < MIN_PX) return false
        out[X] = x0; out[Y] = y0; out[W] = cw; out[H] = ch
        out[BAND_X] = Math.round(bandPx * sx).coerceIn(1, cw / 2)
        out[BAND_Y] = Math.round(bandPx * sy).coerceIn(1, ch / 2)
        return true
    }

    /** One key per crop (x, y, w, h: 16 bits each; samples are at most a few hundred px). */
    fun key(c: IntArray): Long =
        ((c[X].toLong() and 0xFFFF) shl 48) or ((c[Y].toLong() and 0xFFFF) shl 32) or
            ((c[W].toLong() and 0xFFFF) shl 16) or (c[H].toLong() and 0xFFFF)

    /** Bytes of one ARGB_8888 lens bitmap for crop [c]. */
    fun bytes(c: IntArray): Int = c[W] * c[H] * 4
}

/**
 * A tiny least-recently-used cache keyed by primitive longs (no boxing on lookups): at most [max]
 * entries; [onEvict] receives each value pushed out (or cleared). Main thread only.
 */
class LensLru<V : Any>(val max: Int, private val onEvict: (V) -> Unit = {}) {
    private val keys = LongArray(max)
    private val values = arrayOfNulls<Any>(max)
    private val stamps = LongArray(max)
    private var clock = 0L
    var size = 0
        private set

    private fun indexOf(key: Long): Int {
        for (i in 0 until size) if (keys[i] == key) return i
        return -1
    }

    @Suppress("UNCHECKED_CAST")
    fun get(key: Long): V? {
        val i = indexOf(key)
        if (i < 0) return null
        stamps[i] = ++clock
        return values[i] as V
    }

    @Suppress("UNCHECKED_CAST")
    fun put(key: Long, value: V) {
        var i = indexOf(key)
        if (i < 0) {
            if (size < max) i = size++ else {
                i = 0
                for (j in 1 until size) if (stamps[j] < stamps[i]) i = j
                (values[i] as V?)?.let(onEvict)
            }
        } else if (values[i] !== value) (values[i] as V?)?.let(onEvict)
        keys[i] = key; values[i] = value; stamps[i] = ++clock
    }

    @Suppress("UNCHECKED_CAST")
    fun clear() {
        for (i in 0 until size) { (values[i] as V?)?.let(onEvict); values[i] = null }
        size = 0
    }
}
