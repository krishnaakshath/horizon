package dev.tvlauncher.browser.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.ui.Theme.style

/** Overflow menu card anchored under the toolbar's right end. */
class MenuPanel(context: Context) : LinearLayout(context) {
    class Item(val label: () -> String, val action: () -> Unit)

    init {
        orientation = VERTICAL
        val p = context.dp(8)
        setPadding(p, p, p, p)
        background = Theme.rounded(context, Theme.SURFACE_2, 20, Theme.STROKE, 1)
        elevation = context.dpf(16f)
        isClickable = true
        visibility = GONE
    }

    fun open(items: List<Item>, onPick: () -> Unit) {
        removeAllViews()
        val header = TextView(context).apply {
            style(12f, Theme.medium, Theme.TEXT_3)
            text = "BROWSER"
            letterSpacing = 0.12f
            setPadding(context.dp(16), context.dp(8), 0, context.dp(8))
        }
        addView(header)
        items.forEach { item ->
            val row = PillButton(context, item.label(), radiusDp = 14, restColor = 0) { onPick(); item.action() }
            row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            row.setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
            addView(row, LayoutParams(context.dp(280), -2).apply { topMargin = context.dp(2) })
        }
        visibility = VISIBLE
        alpha = 0f; translationY = -context.dpf(8f)
        animate().alpha(1f).translationY(0f).setDuration(160).start()
        getChildAt(1)?.requestFocus()
    }

    fun close() { visibility = GONE }
    val isOpen get() = visibility == VISIBLE
}
