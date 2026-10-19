package dev.tvlauncher.discover

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
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
 * The end-of-shelf "Get more apps" tile: a fill-only rounded card with a plus (no outline), the same outer
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

    private val fill = GradientDrawable().apply { setColor(DiscoverTileSpec.FILL) }
    private val outline = View(context).apply { background = fill }
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
        fill.cornerRadius = dp(TileSpec.CORNER_DP)
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
        fill.setColor(if (gainFocus) DiscoverTileSpec.FILL_FOCUSED else DiscoverTileSpec.FILL)
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
}
