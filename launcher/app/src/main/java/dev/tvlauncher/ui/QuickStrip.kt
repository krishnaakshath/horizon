package dev.tvlauncher.ui

import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.ui.Theme.style

/**
 * Row under the shelf that hosts the dwell quick-strip: a small glass pill of four round buttons
 * (Favorite, Hide, App info, More) that appears under a tile once it has kept focus for
 * [QuickStripState.DWELL_MS]. It never takes focus by itself: DPAD_DOWN on the tile enters it,
 * LEFT/RIGHT move between buttons, UP (or BACK via [returnToTile]) goes back to the tile.
 * Only ViewPropertyAnimator alpha/translation is used and no hardware layers are set.
 */
class QuickStrip(
    context: Context,
    full: Boolean,
    private val shelf: View,
    private val isFavorite: (AppEntry) -> Boolean,
    private val onAction: (Action, AppEntry) -> Unit
) : FrameLayout(context) {
    enum class Action { FAVORITE, HIDE, INFO, MORE }

    val state = QuickStripState()
    private var tile: View? = null
    private var app: AppEntry? = null
    private val dwell = Runnable { maybeShow() }

    private val buttons = Action.values().map { a ->
        val (icon, title) = when (a) {
            Action.FAVORITE -> R.drawable.ic_star_border to "Favorite"
            Action.HIDE -> R.drawable.ic_visibility_off to "Hide"
            Action.INFO -> R.drawable.ic_info to "App info"
            Action.MORE -> R.drawable.ic_more to "More"
        }
        RoundButton(context, icon, title) { app?.let { onAction(a, it) } }
    }
    private val pill = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val p = context.dp(PAD_DP)
        setPadding(p, p, p, p)
        clipChildren = false
        clipToPadding = false
        background = Glass.surface(context, full, Theme.RADIUS_PILL)
    }
    private val label = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.medium)
        gravity = Gravity.CENTER
        alpha = 0f
    }
    private val group = object : LinearLayout(context) {
        override fun hasOverlappingRendering() = false
    }.apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        visibility = View.INVISIBLE
    }

    override fun hasOverlappingRendering() = false

    init {
        clipChildren = false
        clipToPadding = false
        buttons.forEachIndexed { i, b ->
            val ip = context.dp(10)
            b.setPadding(ip, ip, ip, ip)
            pill.addView(b, LinearLayout.LayoutParams(context.dp(BUTTON_DP), context.dp(BUTTON_DP)).apply {
                if (i > 0) marginStart = context.dp(SPACING_DP)
            })
            b.setOnKeyListener { _, code, ev -> ev.action == KeyEvent.ACTION_DOWN && onButtonKey(i, code) }
            b.setOnFocusChangeListener { _, has ->
                if (has) {
                    label.text = b.title
                    label.animate().cancel()
                    label.animate().alpha(1f).setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
                } else post { if (!group.hasFocus()) label.animate().alpha(0f).setDuration(Motion.ms(TileSpec.FOCUS_MS)).start() }
            }
        }
        group.addView(pill, LinearLayout.LayoutParams(-2, -2))
        group.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S1) })
        addView(group, LayoutParams(-2, -2))
    }

    /** True when DPAD_DOWN on the focused tile should enter the strip. */
    val isStripShown: Boolean get() = state.visible && group.visibility == View.VISIBLE

    fun onTileFocused(pos: Int, tileView: View, entry: AppEntry) {
        state.onTileFocused(pos, SystemClock.uptimeMillis())
        tile = tileView
        app = entry
        if (!state.visible) hide()
        schedule()
    }

    fun onTileDpad() {
        state.onDpad(SystemClock.uptimeMillis())
        hide()
        schedule()
    }

    /** Global focus listener hook: [now] is the newly focused view. */
    fun onGlobalFocus(now: View?) {
        when {
            now != null && isInside(now) -> state.onEnterStrip()
            now == null || now.parent !== shelf -> {
                state.onFocusLeftShelf()
                removeCallbacks(dwell)
                hide()
            }
        }
    }

    fun setSpotlightOpen(open: Boolean) {
        state.spotlightOpen = open
        if (open) { removeCallbacks(dwell); hide() } else schedule()
    }

    fun focusFirst(): Boolean = isStripShown && buttons.first().requestFocus()

    fun returnToTile(): Boolean = tile?.takeIf { it.isAttachedToWindow }?.requestFocus() ?: false

    /** Hides immediately (e.g. the shelf is about to rebind after an edit). */
    fun reset() {
        removeCallbacks(dwell)
        state.hide()
        hide()
    }

    private fun isInside(v: View): Boolean {
        var p: Any? = v
        while (p is View) { if (p === this) return true; p = p.parent }
        return false
    }

    private fun schedule() {
        removeCallbacks(dwell)
        postDelayed(dwell, QuickStripState.DWELL_MS)
    }

    private fun maybeShow() {
        val t = tile ?: return
        val a = app ?: return
        // Only for a tile that still has focus in a focused window (not under a dialog).
        if (!t.isAttachedToWindow || !t.hasFocus() || !t.hasWindowFocus()) return
        if (!state.poll(SystemClock.uptimeMillis())) return
        val fav = isFavorite(a)
        buttons[0].setIcon(if (fav) R.drawable.ic_star else R.drawable.ic_star_border, if (fav) "Unfavorite" else "Favorite")
        val shelfAt = IntArray(2).also { shelf.getLocationInWindow(it) }
        val hostAt = IntArray(2).also { getLocationInWindow(it) }
        val cx = shelfAt[0] - hostAt[0] + t.left + t.width / 2f
        val w = group.width.toFloat()
        group.animate().cancel()
        group.translationX = (cx - w / 2f).coerceIn(0f, maxOf(0f, width - w))
        group.translationY = -context.dp(6).toFloat()
        group.alpha = 0f
        group.visibility = View.VISIBLE
        group.animate().alpha(1f).translationY(0f).setDuration(Motion.ms(SHOW_MS)).start()
    }

    private fun hide() {
        if (group.visibility != View.VISIBLE) return
        group.animate().cancel()
        group.animate().alpha(0f).setDuration(Motion.ms(HIDE_MS)).withEndAction { group.visibility = View.INVISIBLE }.start()
    }

    private fun onButtonKey(i: Int, code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> returnToTile()
        KeyEvent.KEYCODE_DPAD_DOWN -> true
        KeyEvent.KEYCODE_DPAD_LEFT -> i == 0
        KeyEvent.KEYCODE_DPAD_RIGHT -> i == buttons.size - 1
        else -> false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(dwell)
        group.animate().cancel()
        label.animate().cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val BUTTON_DP = 44
        const val SPACING_DP = 10
        const val PAD_DP = 6
        const val SHOW_MS = 180L
        const val HIDE_MS = 120L
        /** Height of the row reserved under the shelf: pill + label. */
        const val ROW_DP = BUTTON_DP + 2 * PAD_DP + 4 + 18
    }
}
