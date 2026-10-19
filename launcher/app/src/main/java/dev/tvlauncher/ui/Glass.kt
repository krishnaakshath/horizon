package dev.tvlauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView

/**
 * "Glass" surfaces without blur: a translucent dark fill, a 1dp light border and (FULL only) a
 * faint top highlight. LITE is more opaque and skips the highlight layer.
 */
object Glass {
    const val FILL_FULL = 0xB8151922.toInt()
    const val FILL_LITE = 0xE6151922.toInt()
    const val BORDER = 0x33FFFFFF
    const val HIGHLIGHT = 0x1AFFFFFF

    fun surface(context: Context, full: Boolean, radiusDp: Int): Drawable {
        val r = context.dp(radiusDp).toFloat()
        val base = GradientDrawable().apply {
            setColor(if (full) FILL_FULL else FILL_LITE)
            cornerRadius = r
            setStroke(context.dp(1), BORDER)
        }
        if (!full) return base
        val highlight = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HIGHLIGHT, 0)).apply {
            cornerRadius = r
        }
        return TopLit(arrayOf(base, highlight))
    }

    /** Keeps the highlight layer on the top 40% of whatever bounds the surface gets. */
    private class TopLit(layers: Array<Drawable>) : LayerDrawable(layers) {
        override fun onBoundsChange(bounds: Rect) {
            setLayerInset(1, 0, 0, 0, (bounds.height() * 0.6f).toInt())
            super.onBoundsChange(bounds)
        }
    }
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
