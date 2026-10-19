package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.ScrollTarget
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * A scrolling column of rows: the body of every settings page. Focus-follow keeps the focused
 * row fully on screen with a margin (rows scale on focus, so they are never clipped), and a thin
 * indicator on the right shows the position when the page is taller than the pane.
 * At most one collapsed "Advanced" block per page ([advanced]).
 */
class SettingsPage(context: Context, val style: SettingsStyle, spec: PageSpec? = null) : ScrollView(context) {
    /** Enforces [spec] (declared keys only, at most [PageSpec.MAX_PRIMARY] primary rows); null for free lists. */
    private val guard = spec?.let { RowGuard(it) }

    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(PAD_DP), context.dp(6), context.dp(PAD_DP + 4), context.dp(32))
    }

    /** Rows, notes and labels added to a container: the page itself or its Advanced block. */
    inner class Block internal constructor(val container: LinearLayout, private val advancedBlock: Boolean = false) {
        fun add(view: View): View {
            container.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = context.dp(SettingsLayout.ROW_GAP_DP)
            })
            return view
        }

        fun row(
            key: String,
            title: String,
            kind: RowKind,
            subtitle: String? = null,
            iconRes: Int = 0
        ): SettingsRow {
            guard?.claim(key, advancedBlock)
            return SettingsRow(context, style, key, title, subtitle, iconRes, kind).also { add(it) }
        }

        /** A short, non-focusable note (at most [maxLines] lines, ellipsized). */
        fun note(text: String, maxLines: Int = 3): TextView =
            text(container, text, NOTE_SP, top = 2, bottom = 8, lines = maxLines)

        /** Small uppercase label above a run of rows (use sparingly). */
        fun group(label: String): TextView = TextView(context).apply {
            text = label.uppercase()
            letterSpacing = 0.08f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            style(GROUP_SP, Theme.medium, Theme.TEXT_2)
            container.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = context.dp(14); bottomMargin = context.dp(8); marginStart = context.dp(6)
            })
        }
    }

    /** The page body (as a [Block], for helpers shared with the Advanced block). */
    val body = Block(column)
    /** Row key to focus when the page is opened without a remembered row (e.g. the chosen option). */
    var preferredFocus: String? = null

    private val detachHooks = ArrayList<() -> Unit>()

    /** Runs [f] once when the page leaves the screen (release players, cancel callbacks). */
    fun doOnDetach(f: () -> Unit) {
        detachHooks += f
    }

    override fun onDetachedFromWindow() {
        detachHooks.forEach { it() }
        detachHooks.clear()
        super.onDetachedFromWindow()
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Glass.THUMB }
    private val thumbRect = RectF()

    init {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        // Clip to the page: rows scrolled past the top must never draw over the pane's header.
        // The column keeps clipChildren = false, and its padding leaves room for the focus scale.
        clipChildren = true
        overScrollMode = OVER_SCROLL_NEVER
        if (style.full) {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(20))
        }
        setWillNotDraw(false)
        addView(column, LayoutParams(-1, -2))
    }

    /** A short introduction (wraps to 2 lines). Prefer [SettingsSection.description], shown in the header. */
    fun lead(text: String): TextView = text(column, text, LEAD_SP, top = 2, bottom = 10, lines = 2)

    fun group(label: String): TextView = body.group(label)

    fun note(text: String): TextView = body.note(text)

    fun add(view: View): View = body.add(view)

    fun row(
        key: String,
        title: String,
        kind: RowKind,
        subtitle: String? = null,
        iconRes: Int = 0
    ): SettingsRow = body.row(key, title, kind, subtitle, iconRes)

    /**
     * The page's single "Advanced" row; [fill] adds the secondary rows into a block that is
     * collapsed by default. OK expands it in place and moves focus to its first row; OK again
     * collapses it. The open state survives rebuilds while Settings stays open ([nav]).
     */
    fun advanced(nav: SettingsNav, key: String, summary: String?, fill: (Block) -> Unit): SettingsRow {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        var state = AdvancedToggle(nav.isAdvancedOpen(key))
        lateinit var toggle: SettingsRow
        fun apply() {
            box.visibility = if (state.expanded) View.VISIBLE else View.GONE
            toggle.setExpanded(state.expanded)
        }
        toggle = SettingsRow(context, style, key, "Advanced", summary, R.drawable.ic_set_tune,
            RowKind.Action(chevron = true) {
                state = state.toggle()
                nav.setAdvancedOpen(key, state.expanded)
                apply()
                val rows = ArrayList<View>()
                box.addFocusables(rows, View.FOCUS_DOWN)
                // First advanced row when opening; focus stays on the toggle when closing.
                if (state.focusAfterToggle(0, rows.size) == 1) rows[0].requestFocus()
            })
        body.add(toggle)
        column.addView(box, LinearLayout.LayoutParams(-1, -2))
        fill(Block(box, advancedBlock = true))
        apply()
        return toggle
    }

    private fun text(into: LinearLayout, s: String, sp: Float, top: Int, bottom: Int, lines: Int) = TextView(context).apply {
        text = s
        style(sp, Theme.regular, Theme.TEXT_2)
        setLineSpacing(0f, 1.15f)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        into.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(top); bottomMargin = context.dp(bottom); marginStart = context.dp(6); marginEnd = context.dp(6)
        })
    }

    /** Focus-follow: the focused row ends up fully visible with a margin, never under the edge. */
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0) return 0
        val viewport = height - paddingTop - paddingBottom
        val maxScroll = (column.height - viewport).coerceAtLeast(0)
        val d = ScrollTarget.compute(rect.top, rect.bottom, scrollY, scrollY + viewport, context.dp(FOCUS_MARGIN_DP))
        return d.coerceIn(-scrollY, maxScroll - scrollY)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val inset = context.dp(TRACK_INSET_DP)
        val t = ScrollIndicator.thumb(
            computeVerticalScrollRange(), computeVerticalScrollExtent(), computeVerticalScrollOffset(),
            height - inset * 2, context.dp(24)
        ) ?: return
        // Inside the card's padding: centred in the column's right gutter, right of every row.
        val w = context.dp(SettingsLayout.SCROLLBAR_DP).toFloat()
        val gutter = column.paddingRight.toFloat()
        val x = scrollX + width - gutter / 2f - w / 2f
        val y = scrollY + inset + t.first.toFloat()
        thumbRect.set(x, y, x + w, y + t.second)
        canvas.drawRoundRect(thumbRect, w / 2, w / 2, thumbPaint)
    }

    private companion object {
        const val PAD_DP = SettingsLayout.PANE_PAD_DP
        const val LEAD_SP = 16f
        const val GROUP_SP = 14f
        const val NOTE_SP = 14f
        const val FOCUS_MARGIN_DP = 28
        const val TRACK_INSET_DP = 10
    }
}
