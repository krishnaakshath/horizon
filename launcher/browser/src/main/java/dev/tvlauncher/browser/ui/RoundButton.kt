package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

/**
 * Circular icon button: translucent chip at rest; white fill with a dark glyph when focused
 * (focus mode) or hovered by the virtual cursor.
 */
open class RoundButton(context: Context, iconRes: Int, title: String, onClick: () -> Unit) : ImageView(context) {
    private val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    val isOn get() = isFocused || isHovered

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

    fun setIcon(res: Int) { setImageResource(res); refresh() }

    protected fun refresh() {
        val on = isOn
        fill.setColor(if (on) Theme.TEXT else Theme.CHIP)
        imageTintList = ColorStateList.valueOf(if (on) Theme.TEXT_DARK else Theme.TEXT)
        val s = if (on) 1.08f else 1f
        animate().scaleX(s).scaleY(s).setDuration(Theme.FOCUS_MS).start()
        invalidate()
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

/** Tabs button: the tabs glyph with the open-tab count inside its front card. */
class TabsButton(context: Context, onClick: () -> Unit) : RoundButton(context, dev.tvlauncher.browser.R.drawable.ic_tabs, "Tabs", onClick) {
    var count = 1
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Theme.medium
        textSize = context.dpf(9.5f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = if (isOn) Theme.TEXT_DARK else Theme.TEXT
        val inner = width - paddingLeft - paddingRight
        val cx = paddingLeft + inner * (13.25f / 24f)
        val cy = paddingTop + inner * (14.25f / 24f) - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(count.toString(), cx, cy, paint)
    }
}
