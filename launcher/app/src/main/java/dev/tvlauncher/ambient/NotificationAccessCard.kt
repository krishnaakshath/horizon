package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style

/**
 * Ready-to-host card for granting notification access (one line to wire:
 * `container.addView(NotificationAccessCard(this))`). Shows the current state, a focusable
 * "Open notification access settings" button and, when the TV has no such screen, the ADB line.
 * Call [refresh] from the host's onResume to update the state after the user returns.
 */
class NotificationAccessCard(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val status = TextView(context)
    private val fallback = TextView(context)
    private val button = TextView(context)

    private fun px(dp: Int) = (dp * density + 0.5f).toInt()

    init {
        orientation = VERTICAL
        setPadding(px(20), px(16), px(20), px(16))
        background = GradientDrawable().apply {
            cornerRadius = px(16).toFloat()
            setColor(0x0FFFFFFF)
            setStroke(px(1), Theme.STROKE)
        }
        addView(TextView(context).apply { style(Theme.SP_BUTTON + 1f, Theme.medium); text = "Notifications on the ambient screen" })
        addView(TextView(context).apply {
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
            text = "Lets the ambient screen show up to 3 recent notifications. Kept in memory only, never saved."
        }, params(4))
        status.style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2)
        addView(status, params(10))
        button.apply {
            style(Theme.SP_BUTTON, Theme.medium)
            text = "Open notification access settings"
            isFocusable = true
            isClickable = true
            setPadding(px(16), px(10), px(16), px(10))
            background = buttonBg(false)
            setOnFocusChangeListener { v, f -> v.background = buttonBg(f) }
            setOnClickListener { open() }
        }
        addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        fallback.style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
        fallback.setTextIsSelectable(false)
        fallback.text = NotificationAccess.instructions(settingsOpened = false)
        fallback.visibility = GONE
        addView(fallback, params(10))
        refresh()
    }

    private fun params(top: Int) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(top) }

    private fun buttonBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = px(999).toFloat()
        setColor(if (focused) Theme.TEXT else 0x1FFFFFFF)
    }.also { button.setTextColor(if (focused) Theme.TEXT_DARK else Theme.TEXT) }

    fun refresh() {
        val on = NotificationAccess.isEnabled(context)
        status.text = if (on) "Access: allowed" else "Access: not allowed yet"
        if (on) fallback.visibility = GONE
    }

    private fun open() {
        val opened = NotificationAccess.openSettings(context)
        fallback.text = NotificationAccess.instructions(opened)
        fallback.visibility = VISIBLE
    }
}
