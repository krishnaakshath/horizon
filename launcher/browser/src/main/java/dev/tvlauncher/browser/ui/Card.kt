package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.widget.LinearLayout

/**
 * Focusable + cursor-hoverable container: soft surface at rest; brighter with a white outline
 * (and an optional lift) when focused or hovered. Children never take hover.
 */
open class Card(
    context: Context,
    radiusDp: Int = Theme.RADIUS_TILE,
    private val rest: Int = Theme.SURFACE,
    private val on: Int = Theme.SURFACE_2,
    private val lift: Float = 1.05f,
    private val restStroke: Int = Theme.STROKE
) : LinearLayout(context) {
    val bg = GradientDrawable().apply { cornerRadius = context.dp(radiusDp).toFloat() }
    var onActive: ((Boolean) -> Unit)? = null
    var colors: IntArray? = null
        set(v) { field = v; refresh() }

    init {
        background = bg
        isFocusable = true
        isClickable = true
        refresh()
    }

    val active get() = isFocused || isHovered

    protected fun refresh() {
        val a = active
        val c = colors
        if (c != null) {
            bg.orientation = GradientDrawable.Orientation.TL_BR
            bg.colors = if (a) intArrayOf(lighten(c[0]), lighten(c[1])) else c
        } else bg.setColor(if (a) on else rest)
        bg.setStroke(context.dp(if (a) 2 else 1), if (a) Theme.TEXT else restStroke)
        val s = if (a) lift else 1f
        animate().scaleX(s).scaleY(s).setDuration(Theme.FOCUS_MS).start()
        elevation = if (a && lift > 1f) context.dpf(8f) else 0f
        onActive?.invoke(a)
    }

    private fun lighten(c: Int) = dev.tvlauncher.browser.net.IconColors.blend(c, 0xFF2A3142.toInt(), 0.35f)

    override fun onFocusChanged(g: Boolean, d: Int, r: Rect?) { super.onFocusChanged(g, d, r); refresh() }
    override fun onHoverChanged(h: Boolean) { super.onHoverChanged(h); refresh() }
    override fun onInterceptHoverEvent(event: MotionEvent) = true
}
