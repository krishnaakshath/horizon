package dev.tvlauncher.type

import android.content.Context
import android.util.TypedValue
import android.widget.TextView

/** Named type tokens. Sizes are sp; tracking is em (Android letterSpacing); line height is a multiplier. */
object TextStyles {
    data class Style(
        val name: String,
        val sizeSp: Float,
        val weight: FontWeight,
        val letterSpacingEm: Float,
        /** Effective line height in em (natural line height x multiplier). */
        val lineHeightEm: Float,
        val fontFeatures: String? = null,
        val allCaps: Boolean = false,
        val includeFontPadding: Boolean = true,
    ) {
        val lineHeightMult: Float get() = LineHeight.multiplierFor(LineHeight.INTER_NATURAL_EM, lineHeightEm)
    }

    val Display = Style("Display", 46f, FontWeight.SEMIBOLD, -0.018f, 1.12f, includeFontPadding = false)
    val Title = Style("Title", 30f, FontWeight.SEMIBOLD, -0.012f, 1.20f)
    val Subtitle = Style("Subtitle", 22f, FontWeight.MEDIUM, -0.006f, 1.30f)
    val Body = Style("Body", 18f, FontWeight.REGULAR, -0.006f, 1.40f)
    val Caption = Style("Caption", 14f, FontWeight.REGULAR, 0.01f, 1.35f)
    val Label = Style("Label", 13f, FontWeight.SEMIBOLD, 0.06f, 1.20f, allCaps = true, includeFontPadding = false)
    val ClockLarge = Style("ClockLarge", 112f, FontWeight.LIGHT, -0.015f, 1.0f, "tnum", includeFontPadding = false)
    val ClockSmall = Style("ClockSmall", 28f, FontWeight.MEDIUM, 0f, 1.0f, "tnum", includeFontPadding = false)
    val Numeric = Style("Numeric", 18f, FontWeight.MEDIUM, 0f, 1.20f, "tnum")

    /**
     * Glyph note: the bundled Inter subset DOES contain the triangles, star and check mark
     * (U+25B2, U+25BC, U+2605, U+2713); other scripts (Telugu, Devanagari, CJK, emoji...) fall back to the system
     * fonts per glyph, so keep vector icons for anything decorative.
     */
    val glyphNote: String = "Latin + Latin Extended only; other scripts fall back to system fonts"

    val all: List<Style> = listOf(Display, Title, Subtitle, Body, Caption, Label, ClockLarge, ClockSmall, Numeric)

    fun apply(view: TextView, style: Style, context: Context = view.context) {
        view.typeface = Fonts.typeface(context, style.weight)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
        view.letterSpacing = style.letterSpacingEm // API 21, below minSdk 24
        view.fontFeatureSettings = style.fontFeatures
        view.setLineSpacing(0f, style.lineHeightMult)
        view.includeFontPadding = style.includeFontPadding
        // Lint-clean on purpose: no isAllCaps / setAllCaps (API 28+, minSdk 24). Use our own transformation,
        // and only reset the method if it is ours so password or other methods are never clobbered.
        if (style.allCaps) {
            view.transformationMethod = UpperCaseTransformation
        } else if (view.transformationMethod === UpperCaseTransformation) {
            view.transformationMethod = null
        }
    }
}
