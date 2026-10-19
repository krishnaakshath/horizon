package dev.tvlauncher.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.dp

/**
 * The Everything search's results: section headings and rows in one column, at most
 * [SearchRanker.MAX_ROWS] rows, each [SearchLayout.ROW_DP] tall, so the panel always fits the
 * screen ([SearchLayout.fits]). Rows are plain glass lines that brighten on focus.
 */
class EverythingResults(context: Context) : LinearLayout(context) {
    /** How a row looks: its icon (set by the caller) and the trailing action word. */
    interface Binder {
        fun bindIcon(item: SearchItem, view: ImageView)
        fun actionLabel(item: SearchItem): String?
        fun onPick(item: SearchItem, row: View)
    }

    var binder: Binder? = null

    override fun hasOverlappingRendering() = false

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    fun show(r: SearchResults) {
        removeAllViews()
        for (g in r.groups) {
            addView(header(g.section), LayoutParams(-1, context.dp(SearchLayout.HEADER_DP)))
            for (h in g.hits) addView(row(h.item), LayoutParams(-1, context.dp(SearchLayout.ROW_DP)))
        }
    }

    /** The first focusable row, for "submit" from the search field. */
    fun firstRow(): View? = (0 until childCount).map { getChildAt(it) }.firstOrNull { it.isFocusable }

    private fun header(s: SearchSection) = TextView(context).apply {
        text = s.title.uppercase()
        letterSpacing = 0.08f
        style(11f, Theme.medium, Theme.TEXT_2)
        gravity = Gravity.BOTTOM
        isSingleLine = true
        setPadding(context.dp(12), 0, context.dp(12), context.dp(3))
    }

    private fun row(item: SearchItem): View {
        val r = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            contentDescription = listOfNotNull(item.title, item.subtitle).joinToString(", ")
            val p = context.dp(12)
            setPadding(p, 0, p, 0)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0x26FFFFFF, Theme.TEXT))
                addState(intArrayOf(), rounded(GlassTokens.ROW_REST, 0))
            }
        }
        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; imageTintList = null }
        binder?.bindIcon(item, icon)
        r.addView(icon, LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP)).apply { marginEnd = context.dp(12) })
        r.addView(TextView(context).apply {
            text = item.title
            style(15f, Theme.medium)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(-2, -2).apply { marginEnd = context.dp(10) })
        r.addView(TextView(context).apply {
            text = item.subtitle.orEmpty()
            style(12f, Theme.regular, Theme.TEXT_2)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, -2, 1f))
        binder?.actionLabel(item)?.let { a ->
            r.addView(TextView(context).apply {
                text = a
                style(12f, Theme.medium, Theme.TEXT_2)
                isSingleLine = true
                setPadding(context.dp(10), context.dp(2), context.dp(10), context.dp(2))
                background = GradientDrawable().apply { cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat(); setColor(0x1AFFFFFF) }
            }, LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        }
        r.setOnClickListener { binder?.onPick(item, r) }
        r.setOnFocusChangeListener { v, has ->
            val s = if (has) 1.015f else 1f
            v.animate().cancel()
            v.animate().scaleX(s).scaleY(s).setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
        }
        return r
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.dp(12).toFloat()
        if (stroke != 0) setStroke(context.dp(2), stroke)
    }

    /** Tints a vector icon for a non-app row. */
    fun tintIcon(view: ImageView, res: Int) {
        view.setImageResource(res)
        view.imageTintList = ColorStateList.valueOf(Theme.TEXT)
    }

    companion object {
        const val ICON_DP = 26
    }
}
