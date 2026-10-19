package dev.tvlauncher.type

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.TextView

/** Named type tokens. Sizes are sp; tracking is em (Android letterSpacing); line height is a multiplier. */
object TextStyles {
    data class Style(
        val name: String,
        val sizeSp: Float,
        val weight: FontWeight,
        val letterSpacingEm: Float,
        val lineHeightMult: Float,
        val fontFeatures: String? = null,
        val allCaps: Boolean = false,
    )

    val Display = Style("Display", 46f, FontWeight.SEMIBOLD, -0.005f, 1.05f)
    val Title = Style("Title", 30f, FontWeight.SEMIBOLD, -0.005f, 1.1f)
    val Subtitle = Style("Subtitle", 22f, FontWeight.MEDIUM, 0f, 1.15f)
    val Body = Style("Body", 18f, FontWeight.REGULAR, 0f, 1.25f)
    val Caption = Style("Caption", 14f, FontWeight.REGULAR, 0.01f, 1.2f)
    val Label = Style("Label", 13f, FontWeight.SEMIBOLD, 0.06f, 1.1f, allCaps = true)
    val ClockLarge = Style("ClockLarge", 112f, FontWeight.LIGHT, -0.02f, 1.0f, "tnum")
    val ClockSmall = Style("ClockSmall", 28f, FontWeight.MEDIUM, 0f, 1.0f, "tnum")
    val Numeric = Style("Numeric", 18f, FontWeight.MEDIUM, 0f, 1.1f, "tnum")

    val all: List<Style> = listOf(Display, Title, Subtitle, Body, Caption, Label, ClockLarge, ClockSmall, Numeric)

    fun apply(view: TextView, style: Style, context: Context = view.context) {
        view.typeface = Fonts.typeface(context, style.weight)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
        if (Build.VERSION.SDK_INT >= 21) {
            view.letterSpacing = style.letterSpacingEm
            view.fontFeatureSettings = style.fontFeatures
        }
        view.setLineSpacing(0f, style.lineHeightMult)
        view.isAllCaps = style.allCaps
    }
}
