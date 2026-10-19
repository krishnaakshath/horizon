package dev.tvlauncher.network

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.FocusFinder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/** One place for every panel color, derived from the launcher Theme palette. */
internal object NetColors {
    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)
    val GLASS_FILL = alpha(Theme.SURFACE, 0xCC)
    val GLASS_STROKE = alpha(Theme.TEXT, 0x33)
    val SCRIM = alpha(0x000000, 0x66)
    val TRANSPARENT = alpha(Theme.TEXT, 0x00)
    val CHIP_FILL = alpha(Theme.TEXT, 0x1A)
    val CARD_FILL = alpha(Theme.TEXT, 0x14)
    val PILL_FILL = CHIP_FILL
    val ROW_FOCUS = Theme.TEXT
    val SWITCH_ON = Theme.TEXT_2
    val SWITCH_OFF = alpha(Theme.TEXT_2, 0x55)
    val SWITCH_THUMB_ON = Theme.TEXT_DARK
    val SWITCH_THUMB_OFF = Theme.TEXT_2
}

/** Full-screen scrim that keeps D-pad focus inside the panel while it is open. */
internal class TrapFrame(context: Context) : FrameLayout(context) {
    override fun focusSearch(focused: View?, direction: Int): View? =
        FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
}

/**
 * Glass card anchored under the top bar, top-right, overscan-safe. Opens with a short fade and
 * slide ([lite]: 160 ms and no hardware layer; otherwise 220 ms with a layer only while animating).
 * The host restores focus to the pill that opened the panel after [dismiss].
 * Open/closing state lives in [PanelLifecycle] so a stale close animation never removes a re-opened panel.
 */
internal class PanelShell(private val context: Context, private val lite: Boolean) {
    val root = TrapFrame(context)
    val header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
    private val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val lifecycle = PanelLifecycle()
    var onClosed: (() -> Unit)? = null

    /** True while open (accepts input / re-renders). */
    val isOpen: Boolean get() = lifecycle.isOpen
    /** True until the close animation has finished. */
    val isVisible: Boolean get() = lifecycle.isVisible

    init {
        root.setBackgroundColor(NetColors.SCRIM)
        root.isClickable = true
        // Fallback so focus is trapped even when the panel has no focusable rows.
        root.isFocusable = true
        root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        card.background = GradientDrawable().apply {
            setColor(NetColors.GLASS_FILL)
            cornerRadius = context.dp(RADIUS).toFloat()
            setStroke(context.dp(1), NetColors.GLASS_STROKE)
        }
        val pad = context.dp(Theme.S4)
        card.setPadding(pad, pad, pad, pad)
        card.addView(header, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = context.dp(Theme.S2) })
        root.addView(card, FrameLayout.LayoutParams(context.dp(WIDTH), -2, Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(Theme.MARGIN_V + TOP_BAR_CLEARANCE)
            bottomMargin = context.dp(Theme.MARGIN_V)
            marginEnd = context.dp(Theme.MARGIN_H)
        })
    }

    fun show(host: ViewGroup) {
        if (lifecycle.show() == null) return
        // A fast reopen during the close animation: drop the old animation and its end action.
        root.animate().cancel(); card.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
        host.addView(root, ViewGroup.LayoutParams(-1, -1))
        root.isFocusable = true
        root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        root.alpha = 0f
        card.translationY = -context.dp(14).toFloat()
        val ms = if (lite) LITE_MS else FULL_MS
        card.animate().translationY(0f).setDuration(ms).start()
        val a = root.animate().alpha(1f).setDuration(ms)
        if (!lite) a.withLayer()
        a.start()
        root.requestFocus()
    }

    fun dismiss() {
        val gen = lifecycle.dismiss() ?: return
        // While it fades out, nothing inside may keep or take focus (the host restores it elsewhere).
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.isFocusable = false
        val ms = if (lite) LITE_MS else FULL_MS
        card.animate().translationY(-context.dp(10).toFloat()).setDuration(ms).start()
        val a = root.animate().alpha(0f).setDuration(ms).withEndAction {
            if (lifecycle.finishClose(gen)) {
                (root.parent as? ViewGroup)?.removeView(root)
                onClosed?.invoke()
            }
        }
        if (!lite) a.withLayer()
        a.start()
    }

    /** Immediate teardown without animation. Idempotent. */
    fun release() {
        lifecycle.release()
        root.animate().cancel(); card.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
    }

    /** Rebuilds the list body, keeping the focused row (by key) and scroll position when possible. */
    fun rebuildBody(preferKey: String?, build: () -> Unit) {
        val focusedKey = (root.findFocus() as? PanelRow)?.key
        val hadFocus = root.findFocus() != null
        val sy = scroll.scrollY
        body.removeAllViews()
        build()
        val rows = (0 until body.childCount).mapNotNull { body.getChildAt(it) as? PanelRow }
        val target = rows.firstOrNull { it.key == focusedKey } ?: rows.firstOrNull { it.key == preferKey } ?: rows.firstOrNull()
        if (target != null && (hadFocus || root.findFocus() == null)) {
            target.requestFocus()
            if (target.key == focusedKey) scroll.post { scroll.scrollTo(0, sy) }
        } else if (target == null && root.findFocus() == null) {
            root.requestFocus()
        }
    }

    companion object {
        const val RADIUS = 20
        const val WIDTH = 480
        const val TOP_BAR_CLEARANCE = 56
        const val FULL_MS = 220L
        const val LITE_MS = 160L
    }
}

/** Focusable list row: icon, title, optional subtitle and a trailing view. Focus = white fill like the pills. */
internal class PanelRow(
    context: Context,
    iconRes: Int?,
    title: CharSequence,
    subtitle: CharSequence? = null,
    val key: String = title.toString()
) : LinearLayout(context) {
    private val iconView = ImageView(context)
    private val titleView = TextView(context).apply { style(Theme.SP_SUB, Theme.medium) }
    private val subView = TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2) }
    private val trailing = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val tinted = ArrayList<ImageView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        tag = key
        minimumHeight = context.dp(56)
        setPadding(context.dp(Theme.S3), context.dp(Theme.S2), context.dp(Theme.S3), context.dp(Theme.S2))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(NetColors.ROW_FOCUS))
            addState(intArrayOf(), shape(NetColors.TRANSPARENT))
        }
        if (iconRes != null) {
            iconView.setImageResource(iconRes)
            tinted.add(iconView)
            addView(iconView, LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(Theme.S3) })
        }
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(titleView)
        texts.addView(subView, LayoutParams(-2, -2).apply { topMargin = context.dp(2) })
        addView(texts, LayoutParams(0, -2, 1f))
        addView(trailing, LayoutParams(-2, -2).apply { marginStart = context.dp(Theme.S3) })
        titleView.text = title
        setSubtitle(subtitle)
        applyFocus(false)
    }

    fun setTitle(t: CharSequence) { titleView.text = t }

    fun setSubtitle(s: CharSequence?) {
        subView.text = s ?: ""
        subView.visibility = if (s.isNullOrEmpty()) GONE else VISIBLE
    }

    fun setTrailing(v: View?) {
        trailing.removeAllViews()
        tinted.removeAll { it.parent === trailing }
        if (v != null) trailing.addView(v)
        if (v is ImageView) tinted.add(v)
        applyFocus(isFocused)
    }

    fun trailingIcon(res: Int, sizeDp: Int = 20) {
        val iv = ImageView(context).apply { setImageResource(res) }
        iv.layoutParams = LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
        setTrailing(iv)
    }

    private fun shape(fill: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.dp(14).toFloat()
    }

    private fun applyFocus(f: Boolean) {
        titleView.setTextColor(if (f) Theme.TEXT_DARK else Theme.TEXT)
        subView.setTextColor(if (f) Theme.TEXT_DARK_2 else Theme.TEXT_2)
        val c = ColorStateList.valueOf(if (f) Theme.TEXT_DARK else Theme.TEXT)
        for (t in tinted) t.imageTintList = c
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyFocus(gainFocus)
    }
}

/** Four rising bars; the first [level] (0..4) are lit. */
internal class SignalBars(context: Context) : View(context) {
    var level = 0
        set(v) { field = v.coerceIn(0, 4); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(resolveSize(context.dp(34), w), resolveSize(context.dp(28), h))
    }

    override fun onDraw(canvas: Canvas) {
        val n = 4
        val gap = context.dp(3).toFloat()
        val bw = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = height * (0.34f + 0.22f * i)
            val left = i * (bw + gap)
            rect.set(left, height - bh, left + bw, height.toFloat())
            paint.color = Theme.TEXT
            paint.alpha = if (i < level) 255 else 64
            canvas.drawRoundRect(rect, bw / 2.2f, bw / 2.2f, paint)
        }
    }
}

internal object PanelViews {
    fun chip(context: Context, text: String): TextView = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.medium, Theme.TEXT)
        this.text = text
        setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))
        background = GradientDrawable().apply {
            setColor(NetColors.CHIP_FILL)
            cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat()
            setStroke(context.dp(1), Theme.STROKE)
        }
    }

    fun sectionLabel(context: Context, text: String): TextView = TextView(context).apply {
        style(12f, Theme.medium, Theme.TEXT_2)
        this.text = text.uppercase()
        letterSpacing = 0.08f
        setPadding(context.dp(Theme.S3), context.dp(Theme.S4), context.dp(Theme.S3), context.dp(Theme.S1))
    }

    fun note(context: Context, text: CharSequence, mono: Boolean = false): TextView = TextView(context).apply {
        style(Theme.SP_LABEL, if (mono) Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) else Theme.regular, Theme.TEXT_2)
        this.text = text
        setLineSpacing(0f, 1.15f)
        setPadding(context.dp(Theme.S3), context.dp(Theme.S2), context.dp(Theme.S3), context.dp(Theme.S2))
    }

    fun card(context: Context, fill: Int = NetColors.CARD_FILL): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = context.dp(14).toFloat()
            setStroke(context.dp(1), Theme.STROKE)
        }
        setPadding(context.dp(Theme.S3), context.dp(Theme.S2), context.dp(Theme.S3), context.dp(Theme.S2))
    }

    fun centered(context: Context, vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, context.dp(Theme.S5), 0, context.dp(Theme.S5))
        for (v in views) addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(Theme.S2) })
    }

    fun qualityWord(level: Int) = when (level) {
        4 -> "Excellent signal"
        3 -> "Good signal"
        2 -> "Fair signal"
        1 -> "Weak signal"
        else -> "Very weak signal"
    }
}
