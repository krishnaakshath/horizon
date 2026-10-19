package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.data.Bookmark
import dev.tvlauncher.browser.data.Visit
import dev.tvlauncher.browser.net.FaviconLoader
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.Theme.style
import java.text.DateFormat
import java.util.Date

/** Right-hand sheet listing bookmarks (open / move / delete) or history (per-day, clear). */
class SidePanel(context: Context, private val loader: FaviconLoader) : FrameLayout(context) {
    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Theme.rounded(context, 0xFA141821.toInt(), 24, Theme.STROKE, 1)
        elevation = context.dpf(20f)
        setPadding(context.dp(16), context.dp(18), context.dp(16), context.dp(12))
    }
    private val title = TextView(context).apply { style(22f, Theme.medium) }
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(list) }
    private val empty = TextView(context).apply { style(14f, Theme.regular, Theme.TEXT_3) }
    var onClose: (() -> Unit)? = null

    init {
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
        visibility = GONE
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(actions)
        sheet.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(12) })
        sheet.addView(empty)
        sheet.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(sheet, LayoutParams(context.dp(460), -1, Gravity.END).apply {
            setMargins(0, context.dp(16), context.dp(Theme.MARGIN_H - 8), context.dp(16))
        })
        setOnClickListener { close() }
    }

    val isOpen get() = visibility == VISIBLE

    fun close() {
        if (!isOpen) return
        visibility = GONE
        list.removeAllViews()
        onClose?.invoke()
    }

    private fun open(t: String, emptyText: String, isEmpty: Boolean) {
        title.text = t
        empty.text = emptyText
        empty.visibility = if (isEmpty) VISIBLE else GONE
        visibility = VISIBLE
        sheet.translationX = context.dpf(40f); sheet.alpha = 0f
        sheet.animate().translationX(0f).alpha(1f).setDuration(180).start()
        post { (list.getChildAt(0)?.takeIf { it.isFocusable } ?: firstFocusable(list) ?: actions.getChildAt(0))?.requestFocus() }
    }

    private fun firstFocusable(g: LinearLayout): View? {
        for (i in 0 until g.childCount) { val c = g.getChildAt(i); if (c.isFocusable) return c; if (c is LinearLayout) firstFocusable(c)?.let { return it } }
        return null
    }

    fun showBookmarks(items: List<Bookmark>, onOpen: (String) -> Unit, onMove: (Int, Int) -> Unit, onDelete: (Bookmark) -> Unit) {
        actions.removeAllViews()
        list.removeAllViews()
        items.forEachIndexed { i, b ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(entry(b.title, UrlPolicy.hostOf(b.url), b.url) { onOpen(b.url) }, LinearLayout.LayoutParams(0, context.dp(52), 1f))
            line.addView(small(R.drawable.ic_up, "Move up") { if (i > 0) onMove(i, i - 1) })
            line.addView(small(R.drawable.ic_down, "Move down") { if (i < items.size - 1) onMove(i, i + 1) })
            line.addView(small(R.drawable.ic_trash, "Delete") { onDelete(b) })
            list.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
        }
        open("Bookmarks", "No bookmarks yet. Use the star in the toolbar to add one.", items.isEmpty())
    }

    fun showHistory(sections: List<Pair<String, List<Visit>>>, onOpen: (String) -> Unit, onClear: () -> Unit) {
        actions.removeAllViews()
        list.removeAllViews()
        actions.addView(PillButton(context, "Clear history") { onClear() })
        val tf = DateFormat.getTimeInstance(DateFormat.SHORT)
        sections.forEach { (label, visits) ->
            list.addView(TextView(context).apply {
                style(12f, Theme.medium, Theme.TEXT_3); text = label.uppercase(); letterSpacing = 0.1f
                setPadding(context.dp(8), context.dp(14), 0, context.dp(6))
            })
            visits.forEach { v ->
                val sub = UrlPolicy.hostOf(v.url) + "  ·  " + tf.format(Date(v.time))
                list.addView(entry(v.title.ifBlank { UrlPolicy.hostOf(v.url) }, sub, v.url) { onOpen(v.url) },
                    LinearLayout.LayoutParams(-1, context.dp(52)).apply { topMargin = context.dp(4) })
            }
        }
        open("History", "Pages you visit will show up here.", sections.isEmpty())
    }

    private fun entry(t: String, sub: String, url: String, onClick: () -> Unit): Card =
        Card(context, 14, rest = 0, on = 0x1FFFFFFF, lift = 1f, restStroke = 0).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(10), 0, context.dp(10), 0)
            setOnClickListener { onClick() }
            addView(SiteIcon(context, 30).also { it.bind(url, loader) }, LinearLayout.LayoutParams(context.dp(30), context.dp(30)))
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(context).apply { style(14f, Theme.medium); text = t; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END })
            col.addView(TextView(context).apply { style(11.5f, Theme.regular, Theme.TEXT_3); text = sub; isSingleLine = true },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(3) })
            addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(12) })
        }

    private fun small(icon: Int, label: String, onClick: () -> Unit) =
        RoundButton(context, icon, label, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(context.dp(36), context.dp(36)).apply { marginStart = context.dp(6) }
            val p = context.dp(8); setPadding(p, p, p, p)
        }
}
