package dev.tvlauncher.discover

import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Canvas
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileDims
import dev.tvlauncher.ui.TileSpec

/**
 * The end-of-shelf "Get more apps" tile: a dashed rounded outline with a plus, the same outer
 * size, padding, label slot and focus motion as an app TileView so it sits in the shelf
 * naturally. Click → open [DiscoverPanel].
 */
class DiscoverTileView(
    context: Context,
    private val dims: TileDims = TileSpec.dims(1f),
    onClick: () -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Int) = v * density
    private fun dpi(v: Int) = (v * density + 0.5f).toInt()

    private val outline = DashedCard()
    private val plus = ImageView(context).apply {
        setImageResource(R.drawable.ic_disc_plus)
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0.8f
    }
    private val label = TextView(context).apply {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        style(TileSpec.LABEL_SP.toFloat(), Theme.medium, Theme.TEXT_2)
        gravity = Gravity.CENTER_HORIZONTAL
        text = DiscoverTileSpec.LABEL
    }

    init {
        isFocusable = true
        isClickable = true
        clipChildren = false
        clipToPadding = false
        contentDescription = DiscoverTileSpec.CONTENT_DESCRIPTION
        tag = DiscoverTileSpec.KEY
        layoutParams = LayoutParams(dpi(dims.viewWidthDp), dpi(dims.viewHeightDp))
        val pad = dpi(TileSpec.PAD_DP)
        addView(outline, LayoutParams(dpi(dims.widthDp), dpi(dims.heightDp)).apply { leftMargin = pad; topMargin = pad })
        val p = dpi(DiscoverTileSpec.PLUS_DP)
        addView(plus, LayoutParams(p, p).apply {
            leftMargin = pad + (dpi(dims.widthDp) - p) / 2; topMargin = pad + (dpi(dims.heightDp) - p) / 2
        })
        addView(label, LayoutParams(dpi(dims.widthDp), dpi(TileSpec.LABEL_HEIGHT_DP)).apply {
            leftMargin = pad; topMargin = pad + dpi(dims.heightDp + TileSpec.LABEL_GAP_DP)
        })
        alpha = TileSpec.REST_ALPHA
        setOnClickListener { onClick() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = dpi(TileSpec.PAD_DP) + dpi(dims.heightDp) / 2f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        outline.focused = gainFocus
        label.setTextColor(if (gainFocus) Theme.TEXT else Theme.TEXT_2)
        plus.alpha = if (gainFocus) 1f else 0.8f
        val s = if (gainFocus) TileSpec.FOCUS_SCALE else 1f
        animate().cancel()
        animate().scaleX(s).scaleY(s).alpha(if (gainFocus) TileSpec.FOCUS_ALPHA else TileSpec.REST_ALPHA)
            .setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }

    /** Rounded card with a soft fill and a dashed outline; solid white outline when focused. */
    private inner class DashedCard : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(DiscoverTileSpec.STROKE_DP)
        }
        private val dash = DashPathEffect(floatArrayOf(dp(DiscoverTileSpec.DASH_DP), dp(DiscoverTileSpec.DASH_GAP_DP)), 0f)
        private val rect = RectF()
        var focused = false
            set(v) { field = v; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val inset = stroke.strokeWidth / 2f
            rect.set(inset, inset, width - inset, height - inset)
            val r = dp(TileSpec.CORNER_DP)
            fill.color = if (focused) DiscoverTileSpec.FILL_FOCUSED else DiscoverTileSpec.FILL
            canvas.drawRoundRect(rect, r, r, fill)
            stroke.color = if (focused) DiscoverTileSpec.OUTLINE_FOCUSED else DiscoverTileSpec.OUTLINE
            stroke.pathEffect = if (focused) null else dash
            canvas.drawRoundRect(rect, r, r, stroke)
        }
    }
}
