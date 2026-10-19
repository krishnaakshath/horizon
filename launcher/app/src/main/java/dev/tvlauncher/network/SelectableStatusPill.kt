package dev.tvlauncher.network

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.dp

/**
 * Focusable status pill for the top bar's Wi-Fi / Bluetooth buttons: icon plus optional label,
 * same glass fill, white focused state and 1.06 focus scale as the Inputs pill. With no label it is
 * a round-ish icon button.
 */
class SelectableStatusPill(context: Context, iconRes: Int, label: String? = null, onClick: () -> Unit) : LinearLayout(context) {
    private val iconView = ImageView(context)
    private val labelView = TextView(context).apply { style(Theme.SP_BUTTON, Theme.medium) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        val hasLabel = !label.isNullOrEmpty()
        setPadding(context.dp(if (hasLabel) 12 else 9), context.dp(7), context.dp(if (hasLabel) 16 else 9), context.dp(7))
        iconView.setImageResource(iconRes)
        addView(iconView, LayoutParams(context.dp(22), context.dp(22)))
        if (hasLabel) {
            labelView.text = label
            addView(labelView, LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), pill(Theme.TEXT, 0))
            addState(intArrayOf(), pill(PILL_FILL, Theme.STROKE))
        }
        setOnClickListener { onClick() }
        applyFocus(false)
    }

    fun setIcon(res: Int) = iconView.setImageResource(res)

    fun setLabel(text: String) {
        if (labelView.parent == null) {
            addView(labelView, LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        }
        labelView.text = text
    }

    private fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat()
        if (stroke != 0) setStroke(context.dp(1), stroke)
    }

    private fun applyFocus(f: Boolean) {
        val c = if (f) Theme.TEXT_DARK else Theme.TEXT
        labelView.setTextColor(c)
        iconView.imageTintList = ColorStateList.valueOf(c)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyFocus(gainFocus)
        animate().scaleX(if (gainFocus) 1.06f else 1f).scaleY(if (gainFocus) 1.06f else 1f)
            .setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
    }

    private companion object {
        /** Quiet glass fill, the same as the top-bar pills. */
        const val PILL_FILL = 0x1AF5F7FA
    }
}
