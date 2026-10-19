package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView

/** Same visual system as the launcher: palette, type scale, spacing and radii (dp). */
object Theme {
    const val BASE = 0xFF0B0D12.toInt()
    const val SURFACE = 0xFF151922.toInt()
    const val SURFACE_2 = 0xFF1D2230.toInt()
    const val TEXT = 0xFFF5F7FA.toInt()
    const val TEXT_2 = 0xFFA7B0C0.toInt()
    const val TEXT_3 = 0xFF6B7385.toInt()
    const val TEXT_DARK = 0xFF0B0D12.toInt()
    const val STROKE = 0x1FFFFFFF
    const val ACCENT = 0xFF5B8CFF.toInt()
    const val CHIP = 0x14FFFFFF

    const val MARGIN_H = 40
    const val MARGIN_V = 20
    const val RADIUS_TILE = 18
    const val RADIUS_PILL = 999
    const val FOCUS_MS = 150L

    val medium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    val light: Typeface by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    val regular: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }

    fun TextView.style(sp: Float, face: Typeface, color: Int = TEXT) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        includeFontPadding = false
    }

    fun rounded(context: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = context.dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(context.dp(strokeDp), strokeColor)
        }
}

internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
internal fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
