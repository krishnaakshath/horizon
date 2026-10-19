package dev.tvlauncher.appsettings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * A sidebar entry. Rest: dim text. Selected (its page is shown): soft fill and an accent bar.
 * Focused: a bright white pill with dark text and a slight scale, like the home pills.
 */
internal class SidebarItem(context: Context, private var style: SettingsStyle, val section: SettingsSection) :
    LinearLayout(context) {
    private val bar = View(context)
    private val icon = ImageView(context).apply { setImageResource(section.iconRes) }
    private val label = TextView(context).apply {
        text = section.title
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        style(SP, Theme.medium)
    }
    var isCurrent = false
        set(v) { field = v; isActivated = v; applyColors() }

    init {
        tag = "sidebar.${section.id}"
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = context.dp(46)
        setPadding(context.dp(10), context.dp(8), context.dp(16), context.dp(8))
        addView(bar, LayoutParams(context.dp(4), context.dp(20)).apply { marginEnd = context.dp(12) })
        addView(icon, LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(14) })
        addView(label, LayoutParams(0, -2, 1f))
        applyStyle(style)
    }

    fun applyStyle(s: SettingsStyle) {
        style = s
        bar.background = Glass.pill(context, s.accentOnDark)
        background = StateListDrawable().apply {
            setEnterFadeDuration(s.focusMs.toInt())
            setExitFadeDuration(s.focusMs.toInt())
            addState(intArrayOf(android.R.attr.state_focused), Glass.rect(context, Theme.TEXT, 14))
            addState(intArrayOf(android.R.attr.state_activated), Glass.rect(context, Glass.CHIP, 14))
            addState(intArrayOf(), Glass.rect(context, 0, 14))
        }
        applyColors()
    }

    private fun applyColors() {
        val f = isFocused
        val c = when {
            f -> Theme.TEXT_DARK
            isCurrent -> Theme.TEXT
            else -> Theme.TEXT_2
        }
        label.setTextColor(c)
        icon.imageTintList = ColorStateList.valueOf(c)
        bar.alpha = if (isCurrent && !f) 1f else 0f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyColors()
        val s = if (gainFocus) 1.04f else 1f
        animate().cancel()
        if (style.focusMs > 0) animate().scaleX(s).scaleY(s).setDuration(style.focusMs).start()
        else { scaleX = s; scaleY = s }
    }

    override fun hasOverlappingRendering() = false

    private companion object {
        const val SP = 17f
    }
}

/**
 * A sidebar group header in small caps: OK expands or collapses the group. A chevron shows the
 * state (down when closed, up when open). Focused: the same white pill as the entries.
 */
internal class SidebarHeader(
    context: Context,
    private var style: SettingsStyle,
    val group: SettingsGroup,
    expanded: Boolean
) : LinearLayout(context) {
    private val label = TextView(context).apply {
        text = group.title.uppercase()
        letterSpacing = 0.1f
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        style(SP, Theme.medium, Theme.TEXT_2)
    }
    private val chevron = ImageView(context).apply {
        setImageResource(dev.tvlauncher.R.drawable.ic_set_chevron)
        rotation = if (expanded) -90f else 90f
    }

    init {
        tag = "sidebar.group.${group.name}"
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = context.dp(36)
        setPadding(context.dp(14), context.dp(6), context.dp(14), context.dp(6))
        addView(label, LayoutParams(0, -2, 1f))
        addView(chevron, LayoutParams(context.dp(16), context.dp(16)))
        background = StateListDrawable().apply {
            setEnterFadeDuration(style.focusMs.toInt())
            setExitFadeDuration(style.focusMs.toInt())
            addState(intArrayOf(android.R.attr.state_focused), Glass.rect(context, Theme.TEXT, 12))
            addState(intArrayOf(), Glass.rect(context, 0, 12))
        }
        applyColors()
    }

    private fun applyColors() {
        val c = if (isFocused) Theme.TEXT_DARK else Theme.TEXT_2
        label.setTextColor(c)
        chevron.imageTintList = ColorStateList.valueOf(c)
        chevron.alpha = if (isFocused) 1f else 0.6f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyColors()
        val s = if (gainFocus) 1.03f else 1f
        animate().cancel()
        if (style.focusMs > 0) animate().scaleX(s).scaleY(s).setDuration(style.focusMs).start()
        else { scaleX = s; scaleY = s }
    }

    override fun hasOverlappingRendering() = false

    private companion object {
        const val SP = 13f
    }
}
