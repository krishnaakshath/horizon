package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.net.SuggestionMerger.Kind
import dev.tvlauncher.browser.net.SuggestionMerger.Suggestion
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.Theme.style

/** Dropdown under the address bar: bookmark/history matches first, then search suggestions. */
class SuggestionList(context: Context, private val onPick: (Suggestion) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val p = context.dp(6)
        setPadding(p, p, p, p)
        background = Theme.rounded(context, 0xFF1A1F2B.toInt(), 18, Theme.STROKE, 1)
        elevation = context.dpf(14f)
        visibility = GONE
    }

    fun show(items: List<Suggestion>) {
        removeAllViews()
        if (items.isEmpty()) { visibility = GONE; return }
        items.take(6).forEach { s ->
            val row = Card(context, 12, rest = 0, on = 0x1FFFFFFF, lift = 1f, restStroke = 0).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(14), 0, context.dp(14), 0)
                setOnClickListener { onPick(s) }
            }
            val icon = when (s.kind) { Kind.BOOKMARK -> R.drawable.ic_star; Kind.HISTORY -> R.drawable.ic_history; Kind.SEARCH -> R.drawable.ic_search }
            row.addView(ImageView(context).apply {
                setImageResource(icon); imageTintList = ColorStateList.valueOf(if (s.kind == Kind.SEARCH) Theme.TEXT_2 else Theme.ACCENT)
            }, LayoutParams(context.dp(18), context.dp(18)))
            row.addView(TextView(context).apply {
                style(15f, Theme.regular); text = s.text; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
            s.url?.let { u ->
                row.addView(TextView(context).apply { style(12f, Theme.regular, Theme.TEXT_3); text = UrlPolicy.hostOf(u); isSingleLine = true },
                    LayoutParams(-2, -2).apply { marginStart = context.dp(12) })
            }
            addView(row, LayoutParams(-1, context.dp(42)))
        }
        visibility = VISIBLE
    }

    fun hide() { removeAllViews(); visibility = GONE }
}
