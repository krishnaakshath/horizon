package dev.tvlauncher.browser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.ui.Theme.style

/** Native full-area message (load errors, missing WebView) with up to two actions. */
class MessagePanel(context: Context) : FrameLayout(context) {
    private val icon = ImageView(context)
    private val title = TextView(context).apply { style(24f, Theme.medium); gravity = Gravity.CENTER }
    private val body = TextView(context).apply {
        style(15f, Theme.regular, Theme.TEXT_2); gravity = Gravity.CENTER
        setLineSpacing(context.dpf(4f), 1f)
    }
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    var firstAction: View? = null; private set

    init {
        setBackgroundColor(Theme.BASE)
        isClickable = true
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val badge = FrameLayout(context).apply {
            background = Theme.rounded(context, Theme.SURFACE, 999, Theme.STROKE, 1)
            addView(icon, LayoutParams(context.dp(30), context.dp(30), Gravity.CENTER))
        }
        col.addView(badge, LinearLayout.LayoutParams(context.dp(72), context.dp(72)))
        col.addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(22) })
        col.addView(body, LinearLayout.LayoutParams(context.dp(520), -2).apply { topMargin = context.dp(10) })
        col.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(28) })
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
    }

    fun show(iconRes: Int, iconTint: Int, title: String, body: String, vararg buttons: Pair<String, () -> Unit>) {
        icon.setImageResource(iconRes)
        icon.imageTintList = ColorStateList.valueOf(iconTint)
        this.title.text = title
        this.body.text = body
        actions.removeAllViews()
        firstAction = null
        buttons.forEachIndexed { i, (label, action) ->
            val b = PillButton(context, label, onClick = action)
            actions.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = context.dp(14) })
            if (i == 0) firstAction = b
        }
        visibility = VISIBLE
    }
}
