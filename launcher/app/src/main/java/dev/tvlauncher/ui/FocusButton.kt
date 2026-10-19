package dev.tvlauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** A focusable text button with a visible D-pad focus state. */
class FocusButton(context: Context, text: String, onClick: () -> Unit) : TextView(context) {
    init {
        this.text = text
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(context.dp(24), context.dp(10), context.dp(24), context.dp(10))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(0x55FFFFFF, Color.WHITE, 2))
            addState(intArrayOf(), shape(0x22FFFFFF, Color.TRANSPARENT, 0))
        }
        setOnClickListener { onClick() }
    }

    private fun shape(fill: Int, stroke: Int, strokeDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.dp(12).toFloat()
        if (strokeDp > 0) setStroke(context.dp(strokeDp), stroke)
    }
}
