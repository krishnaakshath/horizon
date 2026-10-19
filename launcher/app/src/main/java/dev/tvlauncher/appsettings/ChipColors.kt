package dev.tvlauncher.appsettings

import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme

/** Status pill colors (from Theme/Glass only) and the WCAG math that keeps their text legible. */
object ChipColors {
    /** How much accent goes into a positive chip over the surface (bright accents stay readable). */
    const val POSITIVE_T = 0.45f

    /** Positive: accent over the surface (opaque). Neutral: a faint wash ([rowFocused] kept for callers). */
    @Suppress("UNUSED_PARAMETER")
    fun fill(tone: Chip, accent: Int, rowFocused: Boolean = false): Int =
        if (tone == Chip.POSITIVE) BrandColors.blend(Theme.SURFACE, accent, POSITIVE_T) else Glass.CHIP

    /** Positive chips always use the bright text; neutral ones follow the row (dim at rest). */
    fun text(tone: Chip, rowFocused: Boolean): Int =
        if (tone == Chip.POSITIVE || rowFocused) Theme.TEXT else Theme.TEXT_2

    /** [top] composited over an opaque [bottom]. */
    fun over(top: Int, bottom: Int): Int {
        val a = ((top ushr 24) and 0xFF) / 255f
        return BrandColors.blend(bottom or (0xFF shl 24), top or (0xFF shl 24), a)
    }

    fun contrast(fg: Int, bg: Int): Double {
        val a = BrandColors.luminance(fg)
        val b = BrandColors.luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
