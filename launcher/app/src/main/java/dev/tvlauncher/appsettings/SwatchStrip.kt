package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/** A row of focusable color swatches; the chosen one shows a dark inner dot and a white ring. */
internal class SwatchStrip(
    context: Context,
    private val style: SettingsStyle,
    keyPrefix: String,
    colors: List<Int>,
    selected: Int,
    private val onPick: (Int) -> Unit
) : LinearLayout(context) {
    private val swatches = colors.mapIndexed { i, c -> Swatch(context, c, "$keyPrefix.$i") }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(18), context.dp(10), context.dp(18), context.dp(14))
        swatches.forEach { s ->
            s.chosen = s.color == selected
            addView(s, LayoutParams(context.dp(SIZE_DP), context.dp(SIZE_DP)).apply { marginEnd = context.dp(GAP_DP) })
            s.setOnClickListener {
                swatches.forEach { o -> o.chosen = o === s; o.invalidate() }
                onPick(s.color)
            }
        }
    }

    /** Shrinks swatches/gaps on a narrow pane so the strip (and a focused, scaled swatch) never leaves the row. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (w > 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val (size, gap) = SwatchFit.fit(w - paddingLeft - paddingRight, swatches.size,
                context.dp(SIZE_DP), context.dp(GAP_DP), FOCUS_SCALE)
            swatches.forEachIndexed { i, s ->
                val lp = s.layoutParams as LayoutParams
                lp.width = size; lp.height = size
                lp.marginEnd = if (i < swatches.size - 1) gap else 0
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private inner class Swatch(context: Context, val color: Int, key: String) : View(context) {
        var chosen = false
        private val d = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@Swatch.color }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; color = Theme.TEXT }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (BrandColors.onColorTextIsLight(this@Swatch.color)) Theme.TEXT else Theme.TEXT_DARK
        }

        init {
            tag = key
            isFocusable = true
            isClickable = true
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val outer = width / 2f
            val r = outer - 5 * d
            canvas.drawCircle(cx, cy, r, fill)
            if (chosen) canvas.drawCircle(cx, cy, 5 * d, dot)
            if (isFocused || chosen) {
                ring.strokeWidth = (if (isFocused) 3f else 1.5f) * d
                ring.alpha = if (isFocused) 255 else 140
                canvas.drawCircle(cx, cy, outer - ring.strokeWidth / 2f, ring)
            }
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            invalidate()
            val s = if (gainFocus) FOCUS_SCALE else 1f
            animate().cancel()
            if (style.focusMs > 0) animate().scaleX(s).scaleY(s).setDuration(style.focusMs).start()
            else { scaleX = s; scaleY = s }
        }
    }

    private companion object {
        const val SIZE_DP = 46
        const val GAP_DP = 14
        const val FOCUS_SCALE = 1.12f
    }
}
