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
    const val BRIGHT_MIN_FLOOR_LUMINANCE = 0.012

    private val CINEMA = Palette(
        backdropFloor = 0xFF040404.toInt(),
        backdropTop = 0xFF0D0C0B.toInt(),
        backdropBottom = 0xFF060606.toInt(),
        surface = 0xFF161412.toInt(),
        surfaceElevated = 0xFF1F1C19.toInt(),
        glassFill = 0xC0141210.toInt(),
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
        backdropFloor = 0xFF0A0908.toInt(),
        backdropTop = 0xFF18150F.toInt(),
        backdropBottom = 0xFF0D0B0A.toInt(),
        surface = 0xFF1C1916.toInt(),
        surfaceElevated = 0xFF25211D.toInt(),
        glassFill = 0xC81A1714.toInt(),
        glassBorder = 0x29FFF2E0,
        textPrimary = 0xFFEBE5DA.toInt(),     // ~92 %, warm
        textSecondary = 0xFFADA69A.toInt(),
        textDisabled = 0xFF6A645C.toInt(),
        accentBlend = 0.45f,
        glowStrength = 0.60f,
        shadowStrength = 0.55f,
        focusRingColor = 0xFFE6DFD3.toInt(),
        focusRingWidthDp = 3f,
        tileDimAlpha = 0.68f,
        textWeightBoost = 0f,
        minTextScale = 1f,
        scrimStrength = 0.50f
    )

    private val STANDARD = Palette(
        backdropFloor = 0xFF111419.toInt(),
        backdropTop = 0xFF1D2129.toInt(),
        backdropBottom = 0xFF14171D.toInt(),
        surface = 0xFF1F232C.toInt(),
        surfaceElevated = 0xFF292E39.toInt(),
        glassFill = 0xD81A1E26.toInt(),
        glassBorder = 0x3DFFFFFF,
        textPrimary = 0xFFF5F7FA.toInt(),
        textSecondary = 0xFFB3BBC8.toInt(),
        textDisabled = 0xFF737B88.toInt(),
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

    private val BRIGHT = Palette(
        backdropFloor = 0xFF1E232C.toInt(),   // dark slate, L = 0.0166
        backdropTop = 0xFF2A303B.toInt(),
        backdropBottom = 0xFF21262F.toInt(),
        surface = 0xFF2E3440.toInt(),
        surfaceElevated = 0xFF383F4C.toInt(),
        glassFill = 0xF0242A35.toInt(),       // 94 % opaque: reflections can't wash the text
        glassBorder = 0x66FFFFFF,
        textPrimary = 0xFFFFFFFF.toInt(),
        textSecondary = 0xFFD2D8E2.toInt(),
        textDisabled = 0xFF8E96A3.toInt(),
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

    private fun channel(a: Int, b: Int, shift: Int, t: Float): Int {
        val ca = (a shr shift) and 255; val cb = (b shr shift) and 255
        if (ca == cb) return ca
        val la = Contrast.toLinear(ca)
        return Contrast.toSrgb(la + (Contrast.toLinear(cb) - la) * t)
    }
}
