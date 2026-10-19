package dev.tvlauncher.room

/**
 * Every colour/strength token that changes with the room's light. Colours are ARGB ints;
 * fractions are 0..1. Consumers (see [RoomTheme]):
 *
 * - [backdropFloor]: nothing on the backdrop may be darker than this (clamp after brand tint).
 * - [backdropTop]/[backdropBottom]: base vertical gradient endpoints before the brand tint.
 * - [accentBlend]: how strongly the app's brand colour tints the backdrop (0 = none, 1 = full).
 * - [surface]/[surfaceElevated]: opaque panels (Settings pane, menus) and raised items on them.
 * - [glassFill]/[glassBorder]: translucent "glass" (Spotlight, quick strip, settings rows).
 * - [textPrimary]/[textSecondary]/[textDisabled]: text on any of the above.
 * - [focusRingColor]/[focusRingWidthDp]: the focus ring on tiles and buttons.
 * - [tileDimAlpha]: alpha of unfocused tiles (1 = no dimming).
 * - [glowStrength]/[shadowStrength]: multipliers (0..1) for the focus glow and drop shadows.
 * - [textWeightBoost]: 0 = normal faces, 1 = use the next heavier face (e.g. light -> regular,
 *   regular -> medium). [minTextScale]: lower bound for text size multipliers (>= 1).
 * - [scrimStrength]: opacity multiplier of the scrim behind hero text over artwork.
 */
data class Palette(
    val backdropFloor: Int,
    val backdropTop: Int,
    val backdropBottom: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val glassFill: Int,
    val glassBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textDisabled: Int,
    val accentBlend: Float,
    val glowStrength: Float,
    val shadowStrength: Float,
    val focusRingColor: Int,
    val focusRingWidthDp: Float,
    val tileDimAlpha: Float,
    val textWeightBoost: Float,
    val minTextScale: Float,
    val scrimStrength: Float
)

/**
 * Mutable twin of [Palette] used by [PaletteAnimator] so a crossfade does not allocate per frame.
 * Main thread only. Field meanings: see [Palette].
 */
class PaletteFrame {
    @JvmField var backdropFloor = 0
    @JvmField var backdropTop = 0
    @JvmField var backdropBottom = 0
    @JvmField var surface = 0
    @JvmField var surfaceElevated = 0
    @JvmField var glassFill = 0
    @JvmField var glassBorder = 0
    @JvmField var textPrimary = 0
    @JvmField var textSecondary = 0
    @JvmField var textDisabled = 0
    @JvmField var accentBlend = 0f
    @JvmField var glowStrength = 0f
    @JvmField var shadowStrength = 0f
    @JvmField var focusRingColor = 0
    @JvmField var focusRingWidthDp = 0f
    @JvmField var tileDimAlpha = 0f
    @JvmField var textWeightBoost = 0f
    @JvmField var minTextScale = 1f
    @JvmField var scrimStrength = 0f

    fun set(p: Palette): PaletteFrame {
        backdropFloor = p.backdropFloor; backdropTop = p.backdropTop; backdropBottom = p.backdropBottom
        surface = p.surface; surfaceElevated = p.surfaceElevated
        glassFill = p.glassFill; glassBorder = p.glassBorder
        textPrimary = p.textPrimary; textSecondary = p.textSecondary; textDisabled = p.textDisabled
        accentBlend = p.accentBlend; glowStrength = p.glowStrength; shadowStrength = p.shadowStrength
        focusRingColor = p.focusRingColor; focusRingWidthDp = p.focusRingWidthDp
        tileDimAlpha = p.tileDimAlpha; textWeightBoost = p.textWeightBoost
        minTextScale = p.minTextScale; scrimStrength = p.scrimStrength
        return this
    }

    fun toPalette() = Palette(
        backdropFloor, backdropTop, backdropBottom, surface, surfaceElevated, glassFill, glassBorder,
        textPrimary, textSecondary, textDisabled, accentBlend, glowStrength, shadowStrength,
        focusRingColor, focusRingWidthDp, tileDimAlpha, textWeightBoost, minTextScale, scrimStrength
    )
}

/**
 * The four tuned palettes and a deterministic blend between any two.
 *
 * Measured WCAG ratios are asserted in PalettesTest and listed in the task-8q report.
 */
object Palettes {
    /** BRIGHT's backdrop (floor, gradient, surface) never goes below this relative luminance. */
    const val BRIGHT_MIN_FLOOR_LUMINANCE = 0.04

    /** Each mode's black-floor relative luminance is at least this multiple of the darker mode's. */
    const val MIN_FLOOR_STEP = 2.0

    /** EVENING's backdrop top is at least this many (R - B) channel steps warmer than CINEMA's. */
    const val MIN_EVENING_WARMTH_SHIFT = 6

    private val CINEMA = Palette(
        backdropFloor = 0xFF040404.toInt(),   // L 0.00121
        backdropTop = 0xFF0D0C0B.toInt(),
        backdropBottom = 0xFF060606.toInt(),
        surface = 0xFF161412.toInt(),
        surfaceElevated = 0xFF1F1C19.toInt(),
        glassFill = 0xC0141210.toInt(),       // 75 %
        glassBorder = 0x1FFFF4E6,
        textPrimary = 0xFFD2CDC5.toInt(),     // 82 % white level, warm-neutral
        textSecondary = 0xFF9C968D.toInt(),
        textDisabled = 0xFF5E5A55.toInt(),
        accentBlend = 0.35f,
        glowStrength = 0.45f,
        shadowStrength = 0.60f,
        focusRingColor = 0xFFCFC9BF.toInt(),
        focusRingWidthDp = 3f,
        tileDimAlpha = 0.62f,
        textWeightBoost = 0f,
        minTextScale = 1f,
        scrimStrength = 0.50f
    )

    private val EVENING = Palette(
        backdropFloor = 0xFF0F0D0B.toInt(),   // L 0.00414 (3.4x CINEMA)
        backdropTop = 0xFF1C1812.toInt(),     // warm: R-B = 10 (CINEMA 2)
        backdropBottom = 0xFF120F0C.toInt(),
        surface = 0xFF211D18.toInt(),
        surfaceElevated = 0xFF2A251F.toInt(),
        glassFill = 0xC81E1A15.toInt(),       // 78 %
        glassBorder = 0x29FFF2E0,
        textPrimary = 0xFFF0E4D0.toInt(),     // warm ivory, R-B = 32 (CINEMA 13)
        textSecondary = 0xFFB3A897.toInt(),
        textDisabled = 0xFF6E665A.toInt(),
        accentBlend = 0.45f,
        glowStrength = 0.60f,
        shadowStrength = 0.55f,
        focusRingColor = 0xFFEDE2CF.toInt(),
        focusRingWidthDp = 3f,
        tileDimAlpha = 0.68f,
        textWeightBoost = 0f,
        minTextScale = 1f,
        scrimStrength = 0.50f
    )

    private val STANDARD = Palette(
        backdropFloor = 0xFF171B22.toInt(),   // L 0.01082 (2.6x EVENING): a real dark gray
        backdropTop = 0xFF232833.toInt(),
        backdropBottom = 0xFF1A1E26.toInt(),
        surface = 0xFF252A35.toInt(),
        surfaceElevated = 0xFF2F3541.toInt(),
        glassFill = 0xD81E222B.toInt(),       // 85 %
        glassBorder = 0x3DFFFFFF,
        textPrimary = 0xFFF5F7FA.toInt(),
        textSecondary = 0xFFB8C0CC.toInt(),
        textDisabled = 0xFF7A8290.toInt(),
        accentBlend = 0.55f,
        glowStrength = 0.70f,
        shadowStrength = 0.50f,
        focusRingColor = 0xFFFFFFFF.toInt(),
        focusRingWidthDp = 3f,
        tileDimAlpha = 0.72f,
        textWeightBoost = 0.15f,
        minTextScale = 1f,
        scrimStrength = 0.55f
    )

    /*
     * BRIGHT floor rationale: a ~250-nit panel at L 0.043 emits ~11 nits; a 3 % reflection of a
     * 300-lux room adds ~3 nits, so the drawn floor still dominates the reflection (the old
     * #1E232C, L 0.017, emitted ~4 nits and lost to it).
     */
    private val BRIGHT = Palette(
        backdropFloor = 0xFF343B49.toInt(),   // L 0.04339 (4.0x STANDARD)
        backdropTop = 0xFF3C4452.toInt(),     // L 0.0570
        backdropBottom = 0xFF373E4C.toInt(),
        surface = 0xFF434B5A.toInt(),         // L 0.0696
        surfaceElevated = 0xFF4C5464.toInt(),
        glassFill = 0xF0394150.toInt(),       // 94 % opaque: reflections can't wash the text
        glassBorder = 0x66FFFFFF,
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xFFDCE1EA.toInt(),
        textDisabled = 0xFF9AA2AF.toInt(),
        accentBlend = 0.30f,                  // flatter, less glossy gradient
        glowStrength = 0.20f,
        shadowStrength = 0.25f,
        focusRingColor = 0xFFFFFFFF.toInt(),
        focusRingWidthDp = 4f,
        tileDimAlpha = 0.82f,
        textWeightBoost = 1f,
        minTextScale = 1.12f,
        scrimStrength = 0.70f
    )

    fun of(mode: RoomMode): Palette = when (mode) {
        RoomMode.CINEMA -> CINEMA
        RoomMode.EVENING -> EVENING
        RoomMode.STANDARD -> STANDARD
        RoomMode.BRIGHT -> BRIGHT
    }

    /** [of] with the user's "bolder text in bright rooms" preference applied. */
    fun of(mode: RoomMode, boostTextInBright: Boolean): Palette {
        val p = of(mode)
        return if (mode == RoomMode.BRIGHT && !boostTextInBright) p.copy(textWeightBoost = 0f, minTextScale = 1f) else p
    }

    /**
     * Blend [a] -> [b] at [t] (clamped to 0..1; NaN = 0). Colours blend in linear light per
     * channel (alpha linearly, not premultiplied, so every channel is monotone in t); floats
     * blend linearly. t = 0 and t = 1 return exactly [a] and [b].
     */
    fun blend(a: Palette, b: Palette, t: Float): Palette = blendInto(a, b, t, PaletteFrame()).toPalette()

    /** Allocation-free [blend] into [out]. */
    fun blendInto(a: Palette, b: Palette, t: Float, out: PaletteFrame): PaletteFrame {
        val x = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)
        if (x <= 0f) return out.set(a)
        if (x >= 1f) return out.set(b)
        out.backdropFloor = color(a.backdropFloor, b.backdropFloor, x)
        out.backdropTop = color(a.backdropTop, b.backdropTop, x)
        out.backdropBottom = color(a.backdropBottom, b.backdropBottom, x)
        out.surface = color(a.surface, b.surface, x)
        out.surfaceElevated = color(a.surfaceElevated, b.surfaceElevated, x)
        out.glassFill = color(a.glassFill, b.glassFill, x)
        out.glassBorder = color(a.glassBorder, b.glassBorder, x)
        out.textPrimary = color(a.textPrimary, b.textPrimary, x)
        out.textSecondary = color(a.textSecondary, b.textSecondary, x)
        out.textDisabled = color(a.textDisabled, b.textDisabled, x)
        out.accentBlend = lerp(a.accentBlend, b.accentBlend, x)
        out.glowStrength = lerp(a.glowStrength, b.glowStrength, x)
        out.shadowStrength = lerp(a.shadowStrength, b.shadowStrength, x)
        out.focusRingColor = color(a.focusRingColor, b.focusRingColor, x)
        out.focusRingWidthDp = lerp(a.focusRingWidthDp, b.focusRingWidthDp, x)
        out.tileDimAlpha = lerp(a.tileDimAlpha, b.tileDimAlpha, x)
        out.textWeightBoost = lerp(a.textWeightBoost, b.textWeightBoost, x)
        out.minTextScale = lerp(a.minTextScale, b.minTextScale, x)
        out.scrimStrength = lerp(a.scrimStrength, b.scrimStrength, x)
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** One ARGB colour, linear-light RGB, linear alpha. No allocation. */
    fun color(a: Int, b: Int, t: Float): Int {
        if (a == b) return a
        val aa = (a ushr 24) and 255; val ab = (b ushr 24) and 255
        val alpha = Math.round(aa + (ab - aa) * t).coerceIn(0, 255)
        return (alpha shl 24) or (channel(a, b, 16, t) shl 16) or (channel(a, b, 8, t) shl 8) or channel(a, b, 0, t)
    }

    internal fun channel(a: Int, b: Int, shift: Int, t: Float): Int {
        val ca = (a shr shift) and 255; val cb = (b shr shift) and 255
        if (ca == cb) return ca
        val la = Contrast.toLinear(ca)
        return Contrast.toSrgb(la + (Contrast.toLinear(cb) - la) * t)
    }
}

/**
 * [Palettes.blendInto] with the endpoint colours' sRGB -> linear conversions done once in
 * [prepare] instead of every frame. Produces exactly the same values as [Palettes.blend].
 * Used by [PaletteAnimator]; allocation-free after construction.
 */
class PaletteBlender {
    private var a: Palette? = null
    private var b: Palette? = null
    private val la = DoubleArray(COLORS * 3)
    private val lb = DoubleArray(COLORS * 3)
    private val ca = IntArray(COLORS)
    private val cb = IntArray(COLORS)
    private val out = IntArray(COLORS)

    fun prepare(from: Palette, to: Palette) {
        a = from; b = to
        fill(from, ca); fill(to, cb)
        for (i in 0 until COLORS) for (k in 0..2) {
            val shift = 16 - 8 * k
            la[i * 3 + k] = Contrast.toLinear((ca[i] shr shift) and 255)
            lb[i * 3 + k] = Contrast.toLinear((cb[i] shr shift) and 255)
        }
    }

    fun blendInto(t: Float, frame: PaletteFrame): PaletteFrame {
        val pa = a ?: return frame
        val pb = b ?: return frame
        val x = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)
        if (x <= 0f) return frame.set(pa)
        if (x >= 1f) return frame.set(pb)
        for (i in 0 until COLORS) {
            val c0 = ca[i]; val c1 = cb[i]
            if (c0 == c1) { out[i] = c0; continue }
            val a0 = (c0 ushr 24) and 255; val a1 = (c1 ushr 24) and 255
            var argb = Math.round(a0 + (a1 - a0) * x).coerceIn(0, 255) shl 24
            for (k in 0..2) {
                val shift = 16 - 8 * k
                val s0 = (c0 shr shift) and 255; val s1 = (c1 shr shift) and 255
                val v = if (s0 == s1) s0 else {
                    val l0 = la[i * 3 + k]
                    Contrast.toSrgb(l0 + (lb[i * 3 + k] - l0) * x)
                }
                argb = argb or (v shl shift)
            }
            out[i] = argb
        }
        // Floats: same arithmetic as Palettes.blendInto.
        frame.accentBlend = pa.accentBlend + (pb.accentBlend - pa.accentBlend) * x
        frame.glowStrength = pa.glowStrength + (pb.glowStrength - pa.glowStrength) * x
        frame.shadowStrength = pa.shadowStrength + (pb.shadowStrength - pa.shadowStrength) * x
        frame.focusRingWidthDp = pa.focusRingWidthDp + (pb.focusRingWidthDp - pa.focusRingWidthDp) * x
        frame.tileDimAlpha = pa.tileDimAlpha + (pb.tileDimAlpha - pa.tileDimAlpha) * x
        frame.textWeightBoost = pa.textWeightBoost + (pb.textWeightBoost - pa.textWeightBoost) * x
        frame.minTextScale = pa.minTextScale + (pb.minTextScale - pa.minTextScale) * x
        frame.scrimStrength = pa.scrimStrength + (pb.scrimStrength - pa.scrimStrength) * x
        frame.backdropFloor = out[0]; frame.backdropTop = out[1]; frame.backdropBottom = out[2]
        frame.surface = out[3]; frame.surfaceElevated = out[4]; frame.glassFill = out[5]
        frame.glassBorder = out[6]; frame.textPrimary = out[7]; frame.textSecondary = out[8]
        frame.textDisabled = out[9]; frame.focusRingColor = out[10]
        return frame
    }

    private fun fill(p: Palette, dst: IntArray) {
        dst[0] = p.backdropFloor; dst[1] = p.backdropTop; dst[2] = p.backdropBottom
        dst[3] = p.surface; dst[4] = p.surfaceElevated; dst[5] = p.glassFill
        dst[6] = p.glassBorder; dst[7] = p.textPrimary; dst[8] = p.textSecondary
        dst[9] = p.textDisabled; dst[10] = p.focusRingColor
    }

    private companion object {
        const val COLORS = 11
    }
}
