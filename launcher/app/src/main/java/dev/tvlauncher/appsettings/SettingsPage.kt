package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * A scrolling column of rows with group labels and notes: the body of every settings page.
 * Rows scale on focus, so the column leaves room around them and does not clip.
 */
class SettingsPage(context: Context, val style: SettingsStyle) : ScrollView(context) {
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(24), context.dp(8), context.dp(24), context.dp(36))
    }

    init {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        clipChildren = false
        overScrollMode = OVER_SCROLL_NEVER
        if (style.full) {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(24))
        }
        addView(column, LayoutParams(-1, -2))
    }

    /** A short introduction under the page title. */
    fun lead(text: String): TextView = text(text, LEAD_SP, Theme.TEXT_2, top = 4, bottom = 12)

    /** Small uppercase label above a group of rows. */
    fun group(label: String): TextView = TextView(context).apply {
        text = label.uppercase()
        letterSpacing = 0.08f
        style(GROUP_SP, Theme.medium, Theme.TEXT_2)
        column.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(20); bottomMargin = context.dp(10); marginStart = context.dp(6)
        })
    }

    fun note(text: String): TextView = text(text, NOTE_SP, Theme.TEXT_2, top = 6, bottom = 6)

    fun add(view: View): View {
        column.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(8) })
        return view
    }

    fun row(
        key: String,
        title: String,
        kind: RowKind,
        subtitle: String? = null,
        iconRes: Int = 0
    ): SettingsRow = SettingsRow(context, style, key, title, subtitle, iconRes, kind).also { add(it) }

    private fun text(s: String, sp: Float, color: Int, top: Int, bottom: Int) = TextView(context).apply {
        text = s
        style(sp, Theme.regular, color)
        setLineSpacing(0f, 1.2f)
        column.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(top); bottomMargin = context.dp(bottom); marginStart = context.dp(6); marginEnd = context.dp(6)
        })
    }

    private companion object {
        const val LEAD_SP = 16f
        const val GROUP_SP = 13f
        const val NOTE_SP = 14f
    }
}
