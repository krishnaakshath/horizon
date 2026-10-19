package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/** Flat "glass" shapes: translucent fills and hairline strokes, no blur (LITE-friendly). */
internal object Glass {
    const val PANEL = Theme.GLASS_PANEL
    const val ROW = Theme.GLASS_ROW
    const val ROW_FOCUS = Theme.GLASS_ROW_FOCUS
    const val CHIP = Theme.GLASS_CHIP
    const val SHEEN = Theme.GLASS_SHEEN
    const val HAIRLINE = Theme.STROKE
    const val SCRIM = Theme.GLASS_SCRIM
    /** Thin scroll indicator thumb: TEXT at about 35%. */
    const val THUMB = (0x59 shl 24) or (Theme.TEXT and 0x00FFFFFF)

    const val RADIUS_PANEL = 28
    const val RADIUS_ROW = 16
    const val RADIUS_CHIP = 12

    fun rect(context: Context, fill: Int, radiusDp: Int, stroke: Int = 0, strokeDp: Int = 1) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = context.dp(radiusDp).toFloat()
            if (stroke != 0) setStroke(context.dp(strokeDp), stroke)
        }

    fun pill(context: Context, fill: Int, stroke: Int = 0) = rect(context, fill, 999, stroke)
}
