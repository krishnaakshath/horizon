package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.Bookmarks
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.ui.Theme.style
import java.util.Calendar

/** Native start page: greeting, a big search pill and 8 quick-link tiles (4 x 2). */
class StartPage(
    context: Context,
    onSearch: () -> Unit,
    onOpen: (String) -> Unit
) : FrameLayout(context) {
    val tiles: List<Tile>
    val searchPill: LinearLayout

    init {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val title = TextView(context).apply {
            style(32f, Theme.light)
            text = greeting()
        }
        val sub = TextView(context).apply {
            style(15f, Theme.regular, Theme.TEXT_2)
            text = "Search the web or pick a quick link"
        }
        col.addView(title, LinearLayout.LayoutParams(-2, -2))
        col.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(10) })

        searchPill = HoverRow(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(20), 0, context.dp(20), 0)
            isFocusable = true
            isClickable = true
            setOnClickListener { onSearch() }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                imageTintList = android.content.res.ColorStateList.valueOf(Theme.TEXT_2)
            }, LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply { marginEnd = context.dp(14) })
            addView(TextView(context).apply {
                style(16f, Theme.regular, Theme.TEXT_3)
                text = "Search with DuckDuckGo or type an address"
            })
        }
        col.addView(searchPill, LinearLayout.LayoutParams(context.dp(560), context.dp(52)).apply {
            topMargin = context.dp(24)
        })

        val grid = GridLayout(context).apply {
            columnCount = 4
            clipChildren = false
            clipToPadding = false
        }
        tiles = Bookmarks.all.map { b -> Tile(context, b) { onOpen(b.url) } }
        val gap = context.dp(16)
        tiles.forEachIndexed { i, t ->
            grid.addView(t, GridLayout.LayoutParams().apply {
                width = context.dp(196); height = context.dp(76)
                if (i % 4 != 0) leftMargin = gap
                if (i >= 4) topMargin = gap
            })
        }
        col.addView(grid, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(32) })
        addView(col, LayoutParams(-2, -2, Gravity.CENTER).apply { bottomMargin = context.dp(16) })
        clipChildren = false
    }

    private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    /** Pill row that lights up on focus or cursor hover. */
    private class HoverRow(context: Context) : LinearLayout(context) {
        private val bg = GradientDrawable().apply { cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat() }
        init { background = bg; refresh() }
        private fun refresh() {
            val on = isFocused || isHovered
            bg.setColor(if (on) Theme.SURFACE_2 else Theme.SURFACE)
            bg.setStroke(context.dp(if (on) 2 else 1), if (on) Theme.TEXT else Theme.STROKE)
        }
        override fun onFocusChanged(g: Boolean, d: Int, r: Rect?) { super.onFocusChanged(g, d, r); refresh() }
        override fun onHoverChanged(h: Boolean) { super.onHoverChanged(h); refresh() }
        override fun onInterceptHoverEvent(event: MotionEvent) = true
    }

    /** Quick-link card: colored letter badge, title and host. */
    class Tile(context: Context, b: Bookmarks.Bookmark, onClick: () -> Unit) : LinearLayout(context) {
        private val bg = GradientDrawable().apply { cornerRadius = context.dp(Theme.RADIUS_TILE).toFloat() }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), 0, context.dp(12), 0)
            background = bg
            isFocusable = true
            isClickable = true
            contentDescription = b.title
            setOnClickListener { onClick() }
            val badge = TextView(context).apply {
                style(20f, Theme.medium, Theme.TEXT)
                text = b.letter.toString()
                gravity = Gravity.CENTER
                background = Theme.rounded(context, b.color, 13)
            }
            addView(badge, LayoutParams(context.dp(44), context.dp(44)))
            val texts = LinearLayout(context).apply { orientation = VERTICAL }
            texts.addView(TextView(context).apply { style(16f, Theme.medium); text = b.title; isSingleLine = true })
            texts.addView(TextView(context).apply {
                style(12f, Theme.regular, Theme.TEXT_2); text = b.host; isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LayoutParams(-2, -2).apply { topMargin = context.dp(5) })
            addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
            refresh()
        }

        private fun refresh() {
            val on = isFocused || isHovered
            bg.setColor(if (on) Theme.SURFACE_2 else Theme.SURFACE)
            bg.setStroke(context.dp(if (on) 2 else 1), if (on) Theme.TEXT else Theme.STROKE)
            val s = if (on) 1.05f else 1f
            animate().scaleX(s).scaleY(s).setDuration(Theme.FOCUS_MS).start()
            elevation = if (on) context.dpf(8f) else 0f
        }

        override fun onFocusChanged(g: Boolean, d: Int, r: Rect?) { super.onFocusChanged(g, d, r); refresh() }
        override fun onHoverChanged(h: Boolean) { super.onHoverChanged(h); refresh() }
        override fun onInterceptHoverEvent(event: MotionEvent) = true
    }
}
