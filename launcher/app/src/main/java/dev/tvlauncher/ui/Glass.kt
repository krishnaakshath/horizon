package dev.tvlauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import dev.tvlauncher.panel.PanelStyle

/**
 * "Glass" surfaces without blur, in the shared panel style ([PanelStyle]): a near-opaque dark
 * fill (96 %, LITE 98 %) with no border, so content underneath never shows through a floating
 * card (Spotlight, the quick-strip) and the layout auditor reads it as opaque.
 */
object Glass {
    const val FILL_FULL = PanelStyle.SURFACE
    const val FILL_LITE = PanelStyle.SURFACE_LITE

    fun surface(context: Context, full: Boolean, radiusDp: Int): Drawable =
        PanelStyle.surface(context, lite = !full, radiusDp = radiusDp)
}

/** Circular focusable icon button; white fill with dark icon when focused. */
class RoundButton(context: Context, iconRes: Int, title: String, onClick: () -> Unit) : ImageView(context) {
    private val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    var title: String = title
        private set

    init {
        isFocusable = true
        isClickable = true
        contentDescription = title
        setImageResource(iconRes)
        scaleType = ScaleType.FIT_CENTER
        background = fill
        setOnClickListener { onClick() }
        applyFocus(false)
    }

    fun setIcon(res: Int, newTitle: String) {
        setImageResource(res)
        title = newTitle
        contentDescription = newTitle
    }

    private fun applyFocus(f: Boolean) {
        fill.setColor(if (f) Theme.TEXT else 0x1AFFFFFF)
        imageTintList = ColorStateList.valueOf(if (f) Theme.TEXT_DARK else Theme.TEXT)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyFocus(gainFocus)
        val s = if (gainFocus) 1.1f else 1f
        animate().cancel()
        animate().scaleX(s).scaleY(s).setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }
}
