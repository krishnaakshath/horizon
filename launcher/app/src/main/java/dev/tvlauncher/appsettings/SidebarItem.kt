package dev.tvlauncher.appsettings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
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
 * The one focus style of Settings (rows, sidebar entries and group headers): accent-lit glass
 * ([SettingsStyle.focusDrawable]) with a 2 dp accent ring, white text and icons, a slight scale.
 * Text colours come from state lists, so a re-created or re-focused view never shows stale ink.
 */
internal object SidebarInk {
    private val FOCUSED = intArrayOf(android.R.attr.state_focused)
    private val ACTIVATED = intArrayOf(android.R.attr.state_activated)
    private val REST = intArrayOf()

    /** Focused and current: bright text; otherwise the dim secondary text. */
    fun list(): ColorStateList = ColorStateList(arrayOf(FOCUSED, ACTIVATED, REST), intArrayOf(Theme.TEXT, Theme.TEXT, Theme.TEXT_2))

    fun background(context: Context, s: SettingsStyle, current: Boolean) = Glass.states(
        context, s.focusMs, Glass.RADIUS_ROW, Glass.rect(context, 0, Glass.RADIUS_ROW),
        activated = if (current) Glass.item(context, fill = Glass.SELECTED) else null,
        focused = s.focusDrawable(context)
    )
}

/**
 * A sidebar entry. Rest: dim text. Current (its page is shown): a soft glass wash and an accent
 * bar. Focused: the shared focus style (accent-lit glass, ring, white text).
 */
internal class SidebarItem(context: Context, private var style: SettingsStyle, val section: SettingsSection) :
    LinearLayout(context) {
    private val bar = View(context)
    private val icon = ImageView(context).apply { setImageResource(section.iconRes); isDuplicateParentStateEnabled = true }
    private val label = TextView(context).apply {
        text = section.title
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        style(SP, Theme.medium)
        isDuplicateParentStateEnabled = true
    }
    var isCurrent = false
        set(v) { field = v; isActivated = v; applyColors() }

    init {
        tag = "sidebar.${section.id}"
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = context.dp(SidebarMetrics.ENTRY_DP)
        setPadding(context.dp(10), 0, context.dp(SidebarMetrics.END_PAD_DP), 0)
        addView(bar, LayoutParams(context.dp(4), context.dp(20)).apply { marginEnd = context.dp(12) })
        addView(icon, LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(14) })
        addView(label, LayoutParams(0, -2, 1f))
        label.setTextColor(SidebarInk.list())
        icon.imageTintList = SidebarInk.list()
        applyStyle(style)
    }

    fun applyStyle(s: SettingsStyle) {
        style = s
        bar.background = Glass.pill(context, s.accentOnDark)
        background = SidebarInk.background(context, s, current = true)
        applyColors()
    }

    private fun applyColors() {
        bar.alpha = if (isCurrent) 1f else 0f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyColors()
        val s = if (gainFocus) FOCUS_SCALE else 1f
        animate().cancel()
        if (style.focusMs > 0) animate().scaleX(s).scaleY(s).setDuration(style.focusMs).start()
        else { scaleX = s; scaleY = s }
    }

    override fun hasOverlappingRendering() = false

    companion object {
        const val SP = 17f
        /** Kept inside the sidebar list's side padding (SettingsLayout.SIDEBAR_LIST_PAD_DP). */
        const val FOCUS_SCALE = 1.03f
    }
}

/**
 * A sidebar group header in small caps: OK expands or collapses the group. The chevron sits
 * right-aligned inside the row (down when closed, up when open). Focus: the same style as entries.
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
        style(SP, Theme.medium)
        isDuplicateParentStateEnabled = true
    }
    private val chevron = ImageView(context).apply {
        setImageResource(dev.tvlauncher.R.drawable.ic_set_chevron)
        rotation = if (expanded) -90f else 90f
        isDuplicateParentStateEnabled = true
    }

    init {
        tag = "sidebar.group.${group.name}"
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = context.dp(SidebarMetrics.HEADER_DP)
        setPadding(context.dp(14), 0, context.dp(SidebarMetrics.END_PAD_DP), 0)
        addView(label, LayoutParams(0, -2, 1f))
        addView(chevron, LayoutParams(context.dp(16), context.dp(16)).apply { marginStart = context.dp(8) })
        background = SidebarInk.background(context, style, current = false)
        label.setTextColor(SidebarInk.list())
        chevron.imageTintList = SidebarInk.list()
        applyColors()
    }

    private fun applyColors() {
        chevron.alpha = if (isFocused) 1f else 0.7f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyColors()
        val s = if (gainFocus) SidebarItem.FOCUS_SCALE else 1f
        animate().cancel()
        if (style.focusMs > 0) animate().scaleX(s).scaleY(s).setDuration(style.focusMs).start()
        else { scaleX = s; scaleY = s }
    }

    override fun hasOverlappingRendering() = false

    private companion object {
        const val SP = 14f
    }
}
