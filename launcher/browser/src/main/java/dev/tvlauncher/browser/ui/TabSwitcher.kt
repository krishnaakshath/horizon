package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.data.Tab
import dev.tvlauncher.browser.net.FaviconLoader
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.Theme.style

/** Full-screen tab switcher: thumbnail cards (LEFT/RIGHT, OK opens), a Close pill under each, New tab. */
class TabSwitcher(context: Context, private val loader: FaviconLoader) : FrameLayout(context) {
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; clipChildren = false }
    private val heading = TextView(context).apply { style(26f, Theme.light) }
    var onClose: (() -> Unit)? = null

    init {
        setBackgroundColor(0xFA0B0D12.toInt())
        isClickable = true
        visibility = GONE
        clipChildren = false
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false }
        col.addView(heading)
        col.addView(TextView(context).apply { style(14f, Theme.regular, Theme.TEXT_2); text = "One page stays live at a time; other tabs reload when you open them." },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(6) })
        col.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(30) })
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
    }

    val isOpen get() = visibility == VISIBLE

    fun open(
        tabs: List<Tab>, activeIndex: Int, thumbs: Map<Long, Bitmap>, canOpen: Boolean,
        onPick: (Int) -> Unit, onCloseTab: (Tab) -> Unit, onNew: () -> Unit
    ) {
        row.removeAllViews()
        heading.text = "Tabs  ·  ${tabs.size} of 3"
        var focusTarget: View? = null
        tabs.forEachIndexed { i, t ->
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false }
            val card = Card(context, 18).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { onPick(i) }
            }
            val thumb = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF1D2230.toInt())
                val r = context.dpf(14f)
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height + r.toInt(), r)
                }
                clipToOutline = true
                val b = thumbs[t.id]
                if (b != null) setImageBitmap(b) else {
                    setImageResource(if (t.url == null) R.drawable.brand_mark else R.drawable.ic_tabs)
                    scaleType = ImageView.ScaleType.CENTER
                    if (t.url != null) imageTintList = ColorStateList.valueOf(Theme.TEXT_3)
                }
            }
            card.addView(thumb, LinearLayout.LayoutParams(context.dp(256), context.dp(144)))
            val info = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(context.dp(12), 0, context.dp(12), 0) }
            if (t.url != null) info.addView(SiteIcon(context, 20).also { it.bind(t.url!!, loader) }, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
            info.addView(TextView(context).apply {
                style(14f, Theme.medium)
                text = if (t.url == null) "Start page" else t.title.ifBlank { UrlPolicy.hostOf(t.url!!) }
                isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(10) })
            if (i == activeIndex) info.addView(TextView(context).apply {
                style(11f, Theme.medium, Theme.TEXT_DARK); text = "CURRENT"
                background = Theme.rounded(context, Theme.TEXT, Theme.RADIUS_PILL)
                setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
            })
            card.addView(info, LinearLayout.LayoutParams(-1, context.dp(44)))
            column.addView(card, LinearLayout.LayoutParams(context.dp(256), -2))
            column.addView(PillButton(context, "Close tab") { onCloseTab(t) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(14) })
            row.addView(column, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = context.dp(24) })
            if (i == activeIndex) focusTarget = card
        }
        if (canOpen) {
            val add = Card(context, 18).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setOnClickListener { onNew() }
                addView(ImageView(context).apply { setImageResource(R.drawable.ic_plus); imageTintList = ColorStateList.valueOf(Theme.TEXT) },
                    LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
                addView(TextView(context).apply { style(14f, Theme.medium); text = "New tab" }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(10) })
            }
            row.addView(add, LinearLayout.LayoutParams(context.dp(150), context.dp(188)).apply { marginStart = context.dp(24) })
        }
        visibility = VISIBLE
        alpha = 0f; animate().alpha(1f).setDuration(160).start()
        post { focusTarget?.requestFocus() }
    }

    fun close() {
        if (!isOpen) return
        visibility = GONE
        row.removeAllViews()
        onClose?.invoke()
    }
}
