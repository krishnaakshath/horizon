package dev.tvlauncher.ui

import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palette

/**
 * The one glass style, as pure tokens (JVM-testable). Every glass surface in the launcher is:
 *
 * 1. FROSTED only (panels over content: Settings panes, Spotlight, the shared panel shell): an
 *    opaque sample of the backdrop behind it. FULL with a pre-blurred 1/8-scale copy of the
 *    backdrop ([GlassBackdrop]); LITE (or until the blur is ready) the backdrop's own gradient in
 *    window coordinates, which is what a blur of a smooth gradient looks like anyway.
 * 2. A translucent tint in the room palette's glass colour ([Palette.glassFill] RGB) whose alpha
 *    is the smallest that keeps [Theme.TEXT_2] (the dimmest body text) at [TARGET_CONTRAST] over
 *    the BRIGHTEST backdrop pixel ([tintAlpha]): a dark room gets real translucency, a bright
 *    accent or wallpaper gets a denser tint.
 * 3. A top-lit sheen: [SHEEN_TOP] fading to nothing over the top [SHEEN_FRACTION] of the surface.
 * 4. A 1 px inner highlight edge, [EDGE_TOP] at the top fading to [EDGE_BOTTOM]: no heavy borders.
 * 5. FULL only: a soft drop shadow (view elevation [SHADOW_DP]).
 *
 * Radii are shared by every glass surface: [RADIUS_PANEL] cards, [RADIUS_ITEM] rows and sidebar
 * entries, [RADIUS_CHIP] icon chips, pills fully round.
 */
object GlassTokens {
    const val RADIUS_PANEL = 24
    const val RADIUS_ITEM = 14
    const val RADIUS_CHIP = 12

    const val SHEEN_TOP = 0x0CFFFFFF
    const val SHEEN_FRACTION = 0.42f
    const val EDGE_TOP = 0x2EFFFFFF
    const val EDGE_BOTTOM = 0x08FFFFFF
    /** Rows and entries on a pane: half the card's edge, so a list never looks boxed in. */
    const val EDGE_TOP_ITEM = 0x17FFFFFF
    const val SHADOW_DP = 10

    /** A resting row/item on a glass pane: a whisper of white. */
    const val ROW_REST = 0x0AFFFFFF
    /** The selected (current) sidebar entry. */
    const val SELECTED = 0x1AFFFFFF
    /** Neutral chips and icon chips on glass. */
    const val CHIP = 0x10FFFFFF
    /** Focused rows, sidebar entries and pills: lit white glass (dark text on it). */
    const val FOCUS_FILL = 0xF5F5F7FA.toInt()
    /** Translucent dark accents drawn on [FOCUS_FILL] (icon chips, off-tracks). */
    const val ON_FOCUS_SUBTLE = 0x1A0B0D12

    const val MIN_TINT_ALPHA = 0.58f
    const val MAX_TINT_ALPHA = 0.97f
    /** WCAG AA for body text plus a small margin for rounding on the panel. */
    const val TARGET_CONTRAST = 4.6

    /** Glass RGB when no room palette has been applied yet (STANDARD's). */
    const val DEFAULT_GLASS_RGB = 0x1E222B

    /**
     * Smallest tint alpha (2 decimals, within [MIN_TINT_ALPHA]..[MAX_TINT_ALPHA]) such that
     * [Theme.TEXT_2] and [Theme.TEXT] keep [TARGET_CONTRAST] on: [brightest] backdrop, then the
     * tint, then the sheen's brightest part, then the brightest wash on glass ([WORST_WASH]).
     */
    fun tintAlpha(glassRgb: Int, brightest: Int): Float {
        var a = MIN_TINT_ALPHA
        while (a < MAX_TINT_ALPHA) {
            if (worstTextContrast(glassRgb, a, brightest) >= TARGET_CONTRAST) return a
            a = Math.round((a + 0.01f) * 100f) / 100f
        }
        return MAX_TINT_ALPHA
    }

    /** The tint colour (ARGB) for [palette] over a backdrop whose brightest colour is [brightest]. */
    fun tint(palette: Palette?, brightest: Int): Int {
        val rgb = glassRgb(palette, brightest)
        return withAlpha(rgb, tintAlpha(rgb, brightest))
    }

    /**
     * The palette's glass colour, darkened toward [Theme.BASE] in 10 % steps only when even the
     * densest tint could not keep the body text readable (BRIGHT's lifted glass under a white
     * wallpaper).
     */
    fun glassRgb(palette: Palette?, brightest: Int): Int {
        val base = (palette?.glassFill ?: DEFAULT_GLASS_RGB) and 0x00FFFFFF
        var t = 0f
        while (t <= 1f) {
            val rgb = BrandColors.blend(base or (0xFF shl 24), Theme.BASE, t) and 0x00FFFFFF
            if (worstTextContrast(rgb, MAX_TINT_ALPHA, brightest) >= TARGET_CONTRAST) return rgb
            t += 0.1f
        }
        return Theme.BASE and 0x00FFFFFF
    }

    /** Lightest tint for CLEAR glass (pills, quick strip): white text and icons only. */
    const val MIN_CLEAR_ALPHA = 0.30f

    /**
     * Tint alpha for CLEAR glass over a backdrop whose brightest colour is [brightest]: the smallest
     * (from [MIN_CLEAR_ALPHA]) keeping [Theme.TEXT] at [TARGET_CONTRAST] on backdrop + tint + sheen.
     */
    fun clearAlpha(glassRgb: Int, brightest: Int): Float {
        var a = MIN_CLEAR_ALPHA
        while (a < MAX_TINT_ALPHA) {
            if (Contrast.ratio(Theme.TEXT, surfaceOn(withAlpha(glassRgb, a), brightest)) >= TARGET_CONTRAST) return a
            a = Math.round((a + 0.01f) * 100f) / 100f
        }
        return MAX_TINT_ALPHA
    }

    fun clearTint(palette: Palette?, brightest: Int): Int {
        val rgb = glassRgb(palette, brightest)
        return withAlpha(rgb, clearAlpha(rgb, brightest))
    }

    /**
     * The brightest white wash ever stacked on glass under text: a resting row plus a neutral chip
     * or icon chip on it (0x0A over 0x10, about 10 %), which also covers [SELECTED].
     */
    const val WORST_WASH = 0x1AFFFFFF

    /** What the dimmest text sits on: backdrop, tint, sheen top and the worst wash (rows, chips, selection). */
    fun restingRowOn(tint: Int, backdrop: Int): Int =
        Contrast.over(WORST_WASH, Contrast.over(SHEEN_TOP, Contrast.over(tint, opaque(backdrop))))

    /** The glass surface itself (no row) over [backdrop]. */
    fun surfaceOn(tint: Int, backdrop: Int): Int = Contrast.over(SHEEN_TOP, Contrast.over(tint, opaque(backdrop)))

    fun worstTextContrast(glassRgb: Int, alpha: Float, brightest: Int): Double {
        val bg = restingRowOn(withAlpha(glassRgb, alpha), brightest)
        return minOf(Contrast.ratio(Theme.TEXT_2, bg), Contrast.ratio(Theme.TEXT, bg))
    }

    /** The brightest (highest relative luminance) of [colors]; opaque BASE when empty. */
    fun brightest(colors: IntArray, count: Int = colors.size): Int {
        var best = Theme.BASE
        var bestL = -1.0
        for (i in 0 until count) {
            val l = Contrast.luminance(colors[i])
            if (l > bestL) { bestL = l; best = colors[i] }
        }
        return opaque(best)
    }

    /** Edge stroke width in px: 1 px up to 1080p, a little more on 4K so it stays visible. */
    fun edgePx(density: Float): Int = maxOf(1, Math.round(density * 0.6f))

    fun withAlpha(rgb: Int, alpha: Float): Int =
        (Math.round(alpha.coerceIn(0f, 1f) * 255f) shl 24) or (rgb and 0x00FFFFFF)

    private fun opaque(c: Int) = c or (0xFF shl 24)
}

/**
 * Separable box blur on opaque ARGB pixels, in place. [passes] box passes approximate a
 * gaussian (3 passes: close enough for frosted glass). Pure; runs on a background thread on a
 * 1/8-scale copy of the backdrop, never per frame.
 */
object BoxBlur {
    fun blur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3) {
        if (w <= 0 || h <= 0 || radius <= 0 || px.size < w * h) return
        val tmp = IntArray(maxOf(w, h))
        repeat(passes) {
            for (y in 0 until h) line(px, y * w, 1, w, radius, tmp)
            for (x in 0 until w) line(px, x, w, h, radius, tmp)
        }
    }

    /** One box pass along a line of [n] pixels starting at [start] with [stride]; edges clamp. */
    private fun line(px: IntArray, start: Int, stride: Int, n: Int, r: Int, tmp: IntArray) {
        for (i in 0 until n) tmp[i] = px[start + i * stride]
        val div = 2 * r + 1
        var sr = 0; var sg = 0; var sb = 0
        for (k in -r..r) {
            val c = tmp[k.coerceIn(0, n - 1)]
            sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
        }
        for (i in 0 until n) {
            px[start + i * stride] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val out = tmp[(i - r).coerceIn(0, n - 1)]
            val inn = tmp[(i + r + 1).coerceIn(0, n - 1)]
            sr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            sg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            sb += (inn and 0xFF) - (out and 0xFF)
        }
    }

    /** Downscale factor of the blurred backdrop copy, and its blur radius at that scale. */
    const val SCALE = 8
    const val RADIUS = 3

    /** Size of the downscaled copy for a [w] x [h] backdrop (never 0). */
    fun scaled(w: Int, h: Int, scale: Int = SCALE): Pair<Int, Int> {
        val k = scale.coerceIn(2, 32)
        return maxOf(1, w / k) to maxOf(1, h / k)
    }

    /** Bytes held by one blurred copy (ARGB_8888). */
    fun bytes(w: Int, h: Int): Int = scaled(w, h).let { (a, b) -> a * b * 4 }
}
