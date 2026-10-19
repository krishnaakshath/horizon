package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

/**
 * Circular toolbar icon button: soft translucent chip at rest; white fill with a dark glyph when
 * focused (focus mode) or hovered by the virtual cursor.
 */
class RoundButton(context: Context, iconRes: Int, title: String, onClick: () -> Unit) : ImageView(context) {
    private val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    init {
        isFocusable = true
        isClickable = true
        contentDescription = title
        setImageResource(iconRes)
        scaleType = ScaleType.FIT_CENTER
        val p = context.dp(11)
        setPadding(p, p, p, p)
        background = fill
        setOnClickListener { onClick() }
        refresh()
    }

    private fun refresh() {
        val on = isFocused || isHovered
        fill.setColor(if (on) Theme.TEXT else Theme.CHIP)
        imageTintList = ColorStateList.valueOf(if (on) Theme.TEXT_DARK else Theme.TEXT)
        val s = if (on) 1.08f else 1f
        animate().scaleX(s).scaleY(s).setDuration(Theme.FOCUS_MS).start()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
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
