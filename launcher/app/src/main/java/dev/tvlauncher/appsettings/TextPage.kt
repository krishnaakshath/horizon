package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.Typeface
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * A long, read-only text (license, notices). The scroll view itself takes focus, so UP/DOWN
 * scroll the text by a step; a soft accent rail on the left shows that it is focused.
 */
internal class TextPage(context: Context, style: SettingsStyle) : ScrollView(context) {
    private val body = TextView(context).apply {
        style(SP, Typeface.MONOSPACE, Theme.TEXT_2)
        setLineSpacing(0f, 1.25f)
        setTextIsSelectable(false)
    }

    init {
        isFocusable = true
        isVerticalScrollBarEnabled = false // no system track over the text; the focus rail shows where it is
        overScrollMode = OVER_SCROLL_NEVER
        setPadding(context.dp(30), context.dp(8), context.dp(30), context.dp(36))
        clipToPadding = false
        addView(body, LayoutParams(-1, -2))
        tag = "text.body"
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), Glass.item(context))
            addState(intArrayOf(), Glass.rect(context, 0, Glass.RADIUS_ROW))
        }
        body.text = "…"
        if (style.full) {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(24))
        }
    }

    fun setText(text: CharSequence) {
        body.text = text
    }

    private companion object {
        const val SP = 14f
    }
}
