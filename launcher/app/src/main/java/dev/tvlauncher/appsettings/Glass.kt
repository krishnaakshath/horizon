package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import dev.tvlauncher.ui.dp

/** Flat "glass" shapes: translucent fills and hairline strokes, no blur (LITE-friendly). */
internal object Glass {
    const val PANEL = 0xD9151922.toInt()
    const val ROW = 0x0FFFFFFF
    const val ROW_FOCUS = 0x26FFFFFF
    const val CHIP = 0x14FFFFFF
    const val HAIRLINE = 0x1FFFFFFF
    const val SCRIM = 0xF2101319.toInt()

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
