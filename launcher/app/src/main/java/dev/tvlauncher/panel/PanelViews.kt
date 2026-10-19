package dev.tvlauncher.panel

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.StateListDrawable
import android.view.FocusFinder
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.ui.HorizonLoader
import dev.tvlauncher.ui.dp

/**
 * The one row style of every panel: leading icon, one-line label, optional value text and a
 * trailing indicator. Focus is a white fill with dark text and icons (like the top-bar pills).
 * [bind] can re-use the view for a new [PanelEntry.Row] with the same key.
 */
class PanelRowView(context: Context, row: PanelEntry.Row) : LinearLayout(context) {
    var row: PanelEntry.Row = row
        private set
    val key: String get() = row.key

    private val icon = ImageView(context)
    private val label = TextView(context).apply { panelText(PanelStyle.ROW_SP, FontWeight.MEDIUM) }
    private val value = TextView(context).apply {
        panelText(PanelStyle.VALUE_SP, FontWeight.REGULAR, PanelStyle.TEXT_2)
        maxWidth = context.dp(200)
    }
    private val end = FrameLayout(context)
    private var endIcon: ImageView? = null
    private var endIconTint = PanelStyle.TEXT_2
    private var switch: PanelSwitch? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = context.dp(PanelLimits.ROW_DP)
        setPadding(context.dp(PanelStyle.G2), 0, context.dp(PanelStyle.G2), 0)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), PanelStyle.rounded(context, PanelStyle.FOCUS_FILL, PanelStyle.ROW_RADIUS))
            addState(intArrayOf(), PanelStyle.rounded(context, PanelStyle.TRANSPARENT, PanelStyle.ROW_RADIUS))
        }
        addView(icon, LayoutParams(context.dp(PanelStyle.ICON_DP), context.dp(PanelStyle.ICON_DP)).apply { marginEnd = context.dp(PanelStyle.G2) })
        addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(PanelStyle.G1) })
        addView(end, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(PanelStyle.G1) })
        bind(row)
    }

    fun bind(r: PanelEntry.Row) {
        row = r
        tag = r.key
        if (r.icon != null) { icon.setImageResource(r.icon); icon.visibility = VISIBLE } else icon.visibility = GONE
        label.text = r.label
        value.text = r.value.orEmpty()
        value.visibility = if (r.value.isNullOrEmpty()) GONE else VISIBLE
        contentDescription = listOfNotNull(r.label, r.value, describe(r.trailing)).joinToString(", ")
        bindTrailing(r.trailing)
        alpha = if (r.enabled) 1f else 0.5f
        applyFocus(isFocused)
    }

    private fun describe(t: Trailing): String? = when (t) {
        is Trailing.Switch -> if (t.on) "on" else "off"
        Trailing.Check -> "selected"
        Trailing.Lock -> "secured"
        Trailing.Busy -> "in progress"
        else -> null
    }

    private fun bindTrailing(t: Trailing) {
        end.removeAllViews()
        endIcon = null
        switch = null
        when (t) {
            Trailing.None -> end.visibility = GONE
            is Trailing.Switch -> {
                switch = PanelSwitch(context).also { it.on = t.on; end.addView(it) }
            }
            Trailing.Busy -> end.addView(HorizonLoader(context, PanelStyle.TEXT_2),
                FrameLayout.LayoutParams(context.dp(PanelStyle.G3 + PanelStyle.G1), context.dp(PanelStyle.G2), Gravity.CENTER))
            Trailing.Check -> addEndIcon(R.drawable.ic_net_check, 20, PanelStyle.ACCENT)
            Trailing.Lock -> addEndIcon(R.drawable.ic_net_lock, 18, PanelStyle.TEXT_2)
            Trailing.Chevron -> addEndIcon(R.drawable.ic_set_chevron, 20, PanelStyle.TEXT_2)
        }
        if (t != Trailing.None) end.visibility = VISIBLE
    }

    private fun addEndIcon(res: Int, sizeDp: Int, tint: Int) {
        val iv = ImageView(context).apply { setImageResource(res) }
        end.addView(iv, FrameLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp), Gravity.CENTER))
        endIcon = iv
        endIconTint = tint
    }

    private fun applyFocus(f: Boolean) {
        label.setTextColor(if (f) PanelStyle.ON_FOCUS else PanelStyle.TEXT)
        value.setTextColor(if (f) PanelStyle.ON_FOCUS_2 else PanelStyle.TEXT_2)
        icon.imageTintList = ColorStateList.valueOf(if (f) PanelStyle.ON_FOCUS else PanelStyle.TEXT)
        endIcon?.imageTintList = ColorStateList.valueOf(if (f) PanelStyle.ON_FOCUS else endIconTint)
        switch?.focused = f
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyFocus(gainFocus)
    }
}

/** On/off switch drawn directly: accent track when on, quiet track when off; no animation, no layers. */
class PanelSwitch(context: Context) : View(context) {
    var on = false
        set(v) { field = v; invalidate() }
    var focused = false
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(40), context.dp(24))

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = when {
            on -> PanelStyle.ACCENT
            focused -> PanelStyle.SWITCH_OFF_FOCUSED
            else -> PanelStyle.SWITCH_OFF
        }
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
        paint.color = if (on || !focused) PanelStyle.TEXT else PanelStyle.ON_FOCUS
        val r = height / 2f - context.dp(3)
        val cx = if (on) width - height / 2f else height / 2f
        canvas.drawCircle(cx, height / 2f, r, paint)
    }
}

/** Non-focusable state line (empty, loading, error, permission needed) in the row grid. */
class PanelMessageView(context: Context, message: PanelEntry.Message) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(PanelLimits.ROW_DP)
        setPadding(context.dp(PanelStyle.G2), context.dp(PanelStyle.G1), context.dp(PanelStyle.G2), context.dp(PanelStyle.G1))
        tag = message.key
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(TextView(context).apply {
            panelText(PanelStyle.MESSAGE_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
            text = message.text
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (message.busy) {
            line.addView(HorizonLoader(context, PanelStyle.TEXT_2),
                LayoutParams(context.dp(PanelStyle.G3 + PanelStyle.G1), context.dp(PanelStyle.G2)).apply { marginStart = context.dp(PanelStyle.G1) })
        }
        addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        message.detail?.let { d ->
            addView(TextView(context).apply {
                panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
                text = d
                alpha = 0.85f
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) })
        }
    }
}

/** Title row: small icon, title and a one-line current-state subtitle. */
class PanelHeaderView(context: Context) : LinearLayout(context) {
    private val icon = ImageView(context).apply { imageTintList = ColorStateList.valueOf(PanelStyle.TEXT) }
    private val title = TextView(context).apply { panelText(PanelStyle.TITLE_SP, FontWeight.SEMIBOLD) }
    private val subtitle = TextView(context).apply { panelText(PanelStyle.DETAIL_SP + 1f, FontWeight.REGULAR, PanelStyle.TEXT_2) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(PanelLimits.HEADER_DP)
        setPadding(context.dp(PanelStyle.G2), 0, context.dp(PanelStyle.G2), 0)
        addView(icon, LayoutParams(context.dp(PanelStyle.HEADER_ICON_DP), context.dp(PanelStyle.HEADER_ICON_DP)).apply { marginEnd = context.dp(PanelStyle.G2) })
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(title)
        texts.addView(subtitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) })
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun bind(h: PanelHeader) {
        icon.imageTintList = ColorStateList.valueOf(PanelStyle.TEXT)
        if (h.icon != null) { icon.setImageResource(h.icon); icon.visibility = VISIBLE } else icon.visibility = GONE
        title.text = h.title
        subtitle.text = h.subtitle
        subtitle.visibility = if (h.subtitle.isEmpty()) GONE else VISIBLE
    }

    /** A full-colour icon (an app icon) in place of the tinted glyph. */
    fun setIcon(d: android.graphics.drawable.Drawable) {
        icon.imageTintList = null
        icon.setImageDrawable(d)
        icon.visibility = VISIBLE
    }
}

/** ScrollView that never grows past [maxHeightPx]; the focused row is kept in view by the framework. */
class MaxHeightScrollView(context: Context) : ScrollView(context) {
    var maxHeightPx = Int.MAX_VALUE
        set(v) { field = v; requestLayout() }

    init {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(context.dp(PanelStyle.G3))
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == MeasureSpec.UNSPECIFIED) maxHeightPx else minOf(size, maxHeightPx)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
    }
}

/** Full-screen dim layer that keeps D-pad focus inside while a panel is open. */
open class TrapFrame(context: Context) : FrameLayout(context) {
    override fun focusSearch(focused: View?, direction: Int): View? =
        FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
}

/**
 * The rows of a panel, rendered from [PanelEntry]s. Used inside [PanelShell] and on its own where
 * a page embeds the same rows (Settings > Devices). Rows with an unchanged key sequence are
 * re-bound in place so focus and scroll stay put.
 */
class PanelList(context: Context) : LinearLayout(context) {
    var onRowClick: ((PanelEntry.Row) -> Unit)? = null
    var onRowKey: ((PanelEntry.Row, Int) -> Boolean)? = null

    init { orientation = VERTICAL }

    fun rowViews(): List<PanelRowView> = (0 until childCount).mapNotNull { getChildAt(it) as? PanelRowView }

    /** Returns true when the views were rebuilt (focus must then be placed by the caller). */
    fun render(entries: List<PanelEntry>): Boolean {
        val existing = (0 until childCount).map { getChildAt(it) }
        val same = existing.size == entries.size && existing.zip(entries).all { (v, e) ->
            (v is PanelRowView && e is PanelEntry.Row && v.key == e.key) ||
                (v is PanelMessageView && e is PanelEntry.Message && v.getTag(R.id.panel_entry) == e)
        }
        if (same) {
            existing.zip(entries).forEach { (v, e) -> if (v is PanelRowView && e is PanelEntry.Row) v.bind(e) }
            return false
        }
        removeAllViews()
        for (e in entries) addView(viewFor(e), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return true
    }

    private fun viewFor(e: PanelEntry): View = when (e) {
        is PanelEntry.Row -> PanelRowView(context, e).apply {
            setOnClickListener { if (row.enabled) onRowClick?.invoke(row) }
            setOnKeyListener { _, code, ev ->
                if (ev.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                if (code != android.view.KeyEvent.KEYCODE_DPAD_LEFT && code != android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return@setOnKeyListener false
                onRowKey?.invoke(row, code) ?: false
            }
        }
        is PanelEntry.Message -> PanelMessageView(context, e).apply { setTag(R.id.panel_entry, e) }
    }
}
