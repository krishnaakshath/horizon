package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import dev.tvlauncher.browser.ui.Theme.style

/** Text button / menu row: quiet chip at rest, white with dark text when focused or hovered. */
class PillButton(
    context: Context,
    label: CharSequence,
    private val radiusDp: Int = Theme.RADIUS_PILL,
    private val restColor: Int = Theme.CHIP,
    onClick: () -> Unit
) : TextView(context) {
    private val fill = GradientDrawable().apply { cornerRadius = context.dp(radiusDp).toFloat() }

    init {
        text = label
        style(15f, Theme.medium)
        gravity = Gravity.CENTER
        val h = context.dp(24); val v = context.dp(13)
        setPadding(h, v, h, v)
        isFocusable = true
        isClickable = true
        background = fill
        setOnClickListener { onClick() }
        refresh()
    }

    private fun refresh() {
        val on = isFocused || isHovered
        fill.setColor(if (on) Theme.TEXT else restColor)
        setTextColor(if (on) Theme.TEXT_DARK else Theme.TEXT)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        refresh()
    }

    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        refresh()
    }
}
