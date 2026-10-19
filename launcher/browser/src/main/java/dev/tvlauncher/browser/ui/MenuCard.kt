package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.browser.ui.Theme.style

/**
 * Floating menu card (browser menu, link/tile context menus). Rows can show a value; LEFT/RIGHT
 * on a row with [Item.adjust] changes it in place, OK runs [Item.action].
 */
class MenuCard(context: Context) : ScrollView(context) {
    class Item(
        val icon: Int, val label: String, val value: (() -> String)? = null,
        val adjust: ((Int) -> Unit)? = null, val keepOpen: Boolean = false, val action: () -> Unit
    )

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    var onClose: (() -> Unit)? = null

    init {
        val p = context.dp(8)
        list.setPadding(p, p, p, p)
        addView(list)
        isVerticalScrollBarEnabled = false
        background = Theme.rounded(context, 0xF71A1F2B.toInt(), 20, Theme.STROKE, 1)
        elevation = context.dpf(18f)
        visibility = GONE
    }

    val isOpen get() = visibility == VISIBLE

    fun open(title: String?, items: List<Item>, widthDp: Int = 300) {
        list.removeAllViews()
        if (title != null) list.addView(TextView(context).apply {
            style(12f, Theme.medium, Theme.TEXT_3); text = title.uppercase(); letterSpacing = 0.1f
            isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(context.dp(14), context.dp(8), context.dp(14), context.dp(8))
        }, LinearLayout.LayoutParams(context.dp(widthDp), -2))
        items.forEach { item -> list.addView(row(item), LinearLayout.LayoutParams(context.dp(widthDp), context.dp(44))) }
        visibility = VISIBLE
        scrollTo(0, 0)
        alpha = 0f; translationY = -context.dpf(6f)
        animate().alpha(1f).translationY(0f).setDuration(150).start()
        post { list.getChildAt(if (title != null) 1 else 0)?.requestFocus() }
    }

    private fun row(item: Item): Card {
        val row = Card(context, 14, rest = 0, on = Theme.TEXT, lift = 1f, restStroke = 0)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(context.dp(14), 0, context.dp(14), 0)
        val icon = ImageView(context).apply { setImageResource(item.icon) }
        val label = TextView(context).apply { style(15f, Theme.medium); text = item.label; isSingleLine = true }
        val value = TextView(context).apply { style(13f, Theme.medium, Theme.TEXT_2); isSingleLine = true }
        row.addView(icon, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
        row.addView(value)
        fun paint(a: Boolean) {
            val fg = if (a) Theme.TEXT_DARK else Theme.TEXT
            icon.imageTintList = ColorStateList.valueOf(fg)
            label.setTextColor(fg)
            value.setTextColor(if (a) 0xCC0B0D12.toInt() else Theme.TEXT_2)
            value.text = item.value?.invoke()?.let { if (item.adjust != null) "‹  $it  ›" else it } ?: ""
        }
        row.onActive = { paint(it) }
        paint(false)
        row.setOnClickListener {
            item.action()
            if (item.keepOpen) paint(row.active) else close()
        }
        if (item.adjust != null) row.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { item.adjust.invoke(-1); paint(true); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { item.adjust.invoke(1); paint(true); true }
                else -> false
            }
        }
        return row
    }

    fun close() {
        if (!isOpen) return
        visibility = GONE
        list.removeAllViews()
        onClose?.invoke()
    }
}
