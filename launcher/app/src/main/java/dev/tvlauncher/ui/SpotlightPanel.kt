package dev.tvlauncher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.component
import dev.tvlauncher.ui.QuickActions.Item
import dev.tvlauncher.ui.Theme.style

/**
 * Spotlight: a glass panel that slides up from the bottom over a dim scrim. Rows: a search
 * field (OK opens the keyboard; typing ranks apps with [SpotlightSearch]), round quick-action
 * buttons ([QuickActions]) and, for a non-empty query, matching apps. While open, D-pad focus
 * is trapped inside the panel. Hidden (GONE) when closed, so it costs nothing at rest.
 */
class SpotlightPanel(
    context: Context,
    private val pm: PackageManager,
    private val full: Boolean,
    private val onAction: (Item) -> Unit,
    private val onActionLongPress: (Item) -> Unit,
    private val onLaunch: (AppEntry, View) -> Unit,
    private val onClosed: () -> Unit
) : FrameLayout(context) {
    var isOpen = false
        private set
    private var apps: List<AppEntry> = emptyList()
    private var shownActions: List<Item> = emptyList()
    private val icons = HashMap<String, Drawable>()
    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val openMs = SpotlightKeys.openMs(full)

    private val scrim = View(context).apply { setBackgroundColor(if (full) 0x73000000 else 0x8C000000.toInt()) }
    private val card = object : LinearLayout(context) {
        override fun hasOverlappingRendering() = false
    }.apply {
        orientation = LinearLayout.VERTICAL
        val p = context.dp(Theme.S5)
        setPadding(p, p, p, p)
        clipChildren = false
        clipToPadding = false
        background = Glass.surface(context, full, CARD_RADIUS_DP)
    }
    val search: EditText = EditText(context).apply {
        hint = "Search apps"
        setHintTextColor(Theme.TEXT_2)
        style(18f, Theme.regular)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        showSoftInputOnFocus = false // focus alone never pops the keyboard; OK does
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(0x26FFFFFF, Theme.TEXT))
            addState(intArrayOf(), rounded(0x14FFFFFF, Theme.STROKE))
        }
        val h = context.dp(Theme.S4)
        setPadding(h, context.dp(Theme.S3), h, context.dp(Theme.S3))
        compoundDrawablePadding = context.dp(Theme.S3)
        setCompoundDrawablesRelativeWithIntrinsicBounds(context.getDrawable(R.drawable.ic_search), null, null, null)
        compoundDrawableTintList = ColorStateList.valueOf(Theme.TEXT_2)
        setOnClickListener { showIme() }
        setOnEditorActionListener { _, id, ev ->
            val enter = ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (id == EditorInfo.IME_ACTION_SEARCH || enter) {
                hideIme()
                if (results.childCount > 0) results.getChildAt(0).requestFocus()
                true
            } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateResults()
        })
    }
    private val actionsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
    private val actionLabel = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2)
        gravity = Gravity.CENTER
    }
    private val results = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        val p = context.dp(Theme.S2)
        setPadding(p, p, p, p)
    }
    private val resultsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        addView(results)
    }
    private val noMatches = TextView(context).apply {
        text = "No matching apps"
        style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
        gravity = Gravity.CENTER
    }
    private val resultsBox = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(resultsScroll, LayoutParams(-1, -2))
        addView(noMatches, LayoutParams(-1, -2, Gravity.CENTER))
        visibility = View.GONE
    }

    override fun hasOverlappingRendering() = false

    init {
        visibility = View.GONE
        clipChildren = false
        addView(scrim, LayoutParams(-1, -1))
        card.addView(search, LinearLayout.LayoutParams(-1, -2))
        card.addView(actionsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S5) })
        card.addView(actionLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
        card.addView(resultsBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S3) })
        addView(card, LayoutParams(context.dp(CARD_W_DP), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = context.dp(Theme.MARGIN_V)
        })
    }

    /** All apps Spotlight can find (refreshed after every reload). */
    fun setApps(list: List<AppEntry>) {
        apps = list
        if (isOpen) updateResults()
    }

    fun open(installedPackages: Set<String>) {
        if (isOpen) return
        isOpen = true
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        val items = QuickActions.items(installedPackages)
        if (items != shownActions) rebuildActions(items)
        search.setText("")
        animate().cancel(); scrim.animate().cancel(); card.animate().cancel()
        visibility = View.VISIBLE
        alpha = 1f
        scrim.alpha = 0f
        card.alpha = 0f
        card.translationY = context.dp(SLIDE_DP).toFloat()
        val decel = DecelerateInterpolator(2f)
        scrim.animate().alpha(1f).setDuration(openMs).setInterpolator(decel).start()
        card.animate().alpha(1f).translationY(0f).setDuration(openMs).setInterpolator(decel).start()
        search.requestFocus()
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        hideIme()
        onClosed() // focus goes back to the shelf while we are still laid out
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        search.setText("")
        icons.clear()
        scrim.animate().cancel(); card.animate().cancel()
        if (!animate) {
            visibility = View.GONE
            return
        }
        val out = openMs * 3 / 4
        val accel = AccelerateInterpolator(1.5f)
        scrim.animate().alpha(0f).setDuration(out).setInterpolator(accel).start()
        card.animate().alpha(0f).translationY(context.dp(SLIDE_DP).toFloat()).setDuration(out)
            .setInterpolator(accel).withEndAction { if (!isOpen) visibility = View.GONE }.start()
    }

    /** Traps D-pad focus inside the panel while it is open. */
    override fun focusSearch(focused: View?, direction: Int): View? {
        if (!isOpen) return super.focusSearch(focused, direction)
        return FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
    }

    private fun rebuildActions(items: List<Item>) {
        shownActions = items
        actionsRow.removeAllViews()
        items.forEachIndexed { i, item ->
            val b = RoundButton(context, iconOf(item), item.title) { onAction(item) }
            val p = context.dp(14)
            b.setPadding(p, p, p, p)
            if (item.hasLongPress) b.setOnLongClickListener { onActionLongPress(item); true }
            b.setOnFocusChangeListener { _, has ->
                if (has) actionLabel.text = item.title
                else post { if (!actionsRow.hasFocus()) actionLabel.text = "" }
            }
            actionsRow.addView(b, LinearLayout.LayoutParams(context.dp(ACTION_DP), context.dp(ACTION_DP)).apply {
                if (i > 0) marginStart = context.dp(Theme.S4)
            })
        }
    }

    private fun iconOf(item: Item) = when (item) {
        Item.INPUTS -> R.drawable.ic_input
        Item.SETTINGS -> R.drawable.ic_settings
        Item.NETWORK -> R.drawable.ic_wifi_3
        Item.BLUETOOTH -> R.drawable.ic_bluetooth
        Item.SOUND -> R.drawable.ic_volume
        Item.INSIGHTS -> R.drawable.ic_insights
        Item.BROWSER -> R.drawable.ic_browser
    }

    private fun updateResults() {
        val q = search.text?.toString().orEmpty()
        val ranked = SpotlightSearch.rank(q, apps)
        val keepFocus = results.hasFocus()
        results.removeAllViews()
        resultsBox.visibility = if (q.isBlank()) View.GONE else View.VISIBLE
        noMatches.visibility = if (ranked.isEmpty()) View.VISIBLE else View.GONE
        resultsScroll.visibility = if (ranked.isEmpty()) View.INVISIBLE else View.VISIBLE
        ranked.forEach { app -> results.addView(resultTile(app)) }
        resultsScroll.scrollTo(0, 0)
        if (keepFocus) (if (results.childCount > 0) results.getChildAt(0) else search).requestFocus()
    }

    private fun resultTile(app: AppEntry): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            contentDescription = app.label
            val p = context.dp(Theme.S2)
            setPadding(p, p, p, p)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(0x26FFFFFF, Theme.TEXT, RESULT_RADIUS_DP))
                addState(intArrayOf(), GradientDrawable())
            }
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(icons.getOrPut(app.packageName) { loadIcon(app) })
        }
        val label = TextView(context).apply {
            text = app.label
            style(Theme.SP_LABEL, Theme.medium)
            gravity = Gravity.CENTER_HORIZONTAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        tile.addView(icon, LinearLayout.LayoutParams(context.dp(RESULT_ICON_DP), context.dp(RESULT_ICON_DP)))
        tile.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
        tile.setOnClickListener { onLaunch(app, tile) }
        tile.setOnFocusChangeListener { v, has ->
            val s = if (has) 1.06f else 1f
            v.animate().cancel()
            v.animate().scaleX(s).scaleY(s).setDuration(TileSpec.FOCUS_MS).start()
        }
        tile.layoutParams = LinearLayout.LayoutParams(context.dp(RESULT_W_DP), -2).apply {
            marginEnd = context.dp(Theme.S2)
        }
        return tile
    }

    private fun loadIcon(app: AppEntry): Drawable = try {
        pm.getActivityIcon(app.component())
    } catch (e: PackageManager.NameNotFoundException) {
        pm.defaultActivityIcon
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int = Theme.RADIUS_PILL) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.dp(radiusDp).toFloat()
        setStroke(context.dp(if (stroke == Theme.TEXT) 2 else 1), stroke)
    }

    private fun showIme() {
        search.requestFocus()
        imm.showSoftInput(search, 0)
    }

    private fun hideIme() {
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        scrim.animate().cancel()
        card.animate().cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CARD_W_DP = 760
        const val CARD_RADIUS_DP = 28
        const val SLIDE_DP = 72
        const val ACTION_DP = 52
        const val RESULT_W_DP = 104
        const val RESULT_ICON_DP = 56
        const val RESULT_RADIUS_DP = 16
    }
}
