package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style

/**
 * Ready-to-host card for granting notification access (one line to wire:
 * `container.addView(NotificationAccessCard(this))`). Calm, friendly states only:
 * - access granted: the card hides itself (GONE);
 * - not granted and the TV has an access screen: a title, one line and a single "Allow" button;
 * - no such screen: one line pointing at the one-time setup ([NotificationAccessText.SETUP_LINE]).
 * No developer commands are ever shown (they live in the docs). Call [refresh] from the host's
 * onResume to update the state after the user returns.
 */
class NotificationAccessCard @JvmOverloads constructor(
    context: Context,
    title: String = DEFAULT_TITLE,
    body: String = DEFAULT_BODY
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val body = TextView(context)
    private val setup = TextView(context)
    private val button = TextView(context)

    private fun px(dp: Int) = (dp * density + 0.5f).toInt()

    init {
        orientation = VERTICAL
        setPadding(px(20), px(16), px(20), px(16))
        background = GradientDrawable().apply {
            cornerRadius = px(14).toFloat()
            setColor(0x0AFFFFFF) // the shared resting glass wash (GlassTokens.ROW_REST)
        }
        addView(TextView(context).apply { style(Theme.SP_BUTTON + 2f, Theme.medium); text = title; maxLines = 2 })
        this.body.apply {
            style(14f, Theme.regular, Theme.TEXT_2)
            text = body
            maxLines = 3
        }
        addView(this.body, params(4))
        setup.style(14f, Theme.regular, Theme.TEXT_2)
        setup.text = NotificationAccessText.SETUP_LINE
        addView(setup, params(8))
        button.apply {
            style(Theme.SP_BUTTON, Theme.medium)
            text = "Allow"
            isFocusable = true
            isClickable = true
            gravity = android.view.Gravity.CENTER
            minWidth = px(120)
            minHeight = px(44)
            setPadding(px(22), px(10), px(22), px(10))
            background = buttonBg(false)
            setOnFocusChangeListener { v, f -> v.background = buttonBg(f) }
            setOnClickListener { open() }
        }
        addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) })
        refresh()
    }

    private fun params(top: Int) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(top) }

    private fun buttonBg(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = px(999).toFloat()
        setColor(if (focused) Theme.TEXT else 0x1FFFFFFF)
    }.also { button.setTextColor(if (focused) Theme.TEXT_DARK else Theme.TEXT) }

    /** Re-reads whether access is granted. Call from the host's onResume (the user returns from Settings). */
    fun refresh() {
        val on = NotificationAccess.isEnabled(context)
        visibility = if (on) GONE else VISIBLE
        if (on) return
        val screen = NotificationAccess.hasSettingsScreen(context)
        button.visibility = if (screen) VISIBLE else GONE
        setup.visibility = if (screen) GONE else VISIBLE
    }

    /** Back from the system's access screen: the window regains focus, so the state is re-read here too. */
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refresh()
    }

    private fun open() {
        val opened = NotificationAccess.openSettings(context)
        if (!opened) {
            button.visibility = GONE
            setup.visibility = VISIBLE
        }
    }

    companion object {
        const val DEFAULT_TITLE = "Notifications on the ambient screen"
        const val DEFAULT_BODY = "Shows up to 3 recent notifications. Kept in memory only, never saved."
        const val NOW_PLAYING_TITLE = "Allow Now Playing"
        const val NOW_PLAYING_BODY = "Shows what's playing, with pause and skip. Nothing is saved."
    }
}
