package dev.tvlauncher.switcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileArt
import dev.tvlauncher.ui.dp

/**
 * The Recent apps strip: a glass band over the bottom of the home screen with the last apps as
 * cards (art, name, and what is playing when the app has an active media session). Keys come
 * from the host through [onKey] while [isOpen]: LEFT/RIGHT move, OK opens, a held OK or Menu
 * asks to close the app (OK again closes, Back keeps it), Back leaves. Never takes the volume
 * keys. The host decides what opening and closing an app means.
 */
class RecentsStrip(
    context: Context,
    private val full: Boolean,
    private val host: Host
) : FrameLayout(context) {
    interface Host {
        fun onOpen(app: AppEntry, from: View)
        fun onCloseApp(card: RecentCard)
        fun onCloseRefused(message: String)
        fun onOpenSetup()
        fun onLeave()
        fun onMove()
    }

    var isOpen = false
        private set

    private val scrim = View(context).apply {
        background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE60B0D12.toInt(), 0x000B0D12))
        auditTag(AuditTags.SCRIM)
    }
    private val title = TextView(context).apply { style(Theme.SP_SUB, Theme.semibold); text = SwitcherText.TITLE }
    private val keysHint = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
        text = SwitcherText.KEYS
        gravity = Gravity.END
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val status = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        val v = context.dp(10)
        setPadding(context.dp(6), v, context.dp(6), v)
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        addView(row, LayoutParams(-2, -2))
    }
    private val card = object : LinearLayout(context) {
        override fun hasOverlappingRendering() = false
    }.apply {
        orientation = LinearLayout.VERTICAL
        val p = context.dp(Theme.S5)
        setPadding(p, context.dp(Theme.S4), p, context.dp(Theme.S4))
        clipChildren = false
        clipToPadding = false
        Glass.frost(this, full, GlassTokens.RADIUS_PANEL)
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(-2, -2))
            addView(keysHint, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(Theme.S4) })
        }
        addView(head, LinearLayout.LayoutParams(-1, -2))
        addView(scroller, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S1) })
        addView(status, LinearLayout.LayoutParams(-1, -2))
    }

    private var cards: List<RecentCard> = emptyList()
    private val targets = ArrayList<View>()
    private var index = 0
    private var confirming = -1
    private var okDown = false
    private var okLong = false
    private var hint: String? = null

    init {
        auditTag(AuditTags.FULLBLEED, AuditTags.IGNORE) // transient overlay
        visibility = View.GONE
        clipChildren = false
        addView(scrim, LayoutParams(-1, context.dp(SCRIM_DP), Gravity.BOTTOM))
        addView(card, LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            val h = context.dp(Theme.MARGIN_H)
            setMargins(h, 0, h, context.dp(Theme.MARGIN_V))
        })
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }

    private val openMs: Long get() = Motion.ms(if (full) 200L else 150L)

    fun open() {
        if (isOpen) return
        isOpen = true
        confirming = -1
        okDown = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        visibility = View.VISIBLE
        card.animate().cancel(); scrim.animate().cancel()
        scrim.alpha = 0f
        card.alpha = 0f
        card.translationY = context.dp(SLIDE_DP).toFloat()
        val decel = DecelerateInterpolator(2f)
        scrim.animate().alpha(1f).setDuration(openMs).setInterpolator(decel).start()
        card.animate().alpha(1f).translationY(0f).setDuration(openMs).setInterpolator(decel).start()
        index = 0
        focusIndex()
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        confirming = -1
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        card.animate().cancel(); scrim.animate().cancel()
        val ms = if (animate) openMs * 3 / 4 else 0L
        if (ms == 0L) { visibility = View.GONE; return }
        scrim.animate().alpha(0f).setDuration(ms).start()
        card.animate().alpha(0f).translationY(context.dp(SLIDE_DP).toFloat()).setDuration(ms)
            .withEndAction { if (!isOpen) visibility = View.GONE }.start()
    }

    /** New cards (from the loader); [accessHint] adds the usage-access line as the last stop. */
    fun show(list: List<RecentCard>, accessHint: Boolean) {
        val focusedPkg = cards.getOrNull(index)?.app?.packageName
        cards = list
        hint = if (accessHint) SwitcherText.ACCESS_HINT else null
        confirming = -1
        rebuild()
        val keep = list.indexOfFirst { it.app.packageName == focusedPkg }
        index = if (keep >= 0) keep else 0
        if (isOpen) focusIndex()
        setStatus(if (list.isEmpty()) SwitcherText.EMPTY else null)
    }

    fun setStatus(text: String?) {
        status.text = text.orEmpty()
        status.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Removes a closed app's card; focus stays at the same place. */
    fun remove(pkg: String) {
        val i = cards.indexOfFirst { it.app.packageName == pkg }
        if (i < 0) return
        cards = cards.filterIndexed { j, _ -> j != i }
        confirming = -1
        rebuild()
        index = index.coerceAtMost(targets.size - 1).coerceAtLeast(0)
        if (isOpen) focusIndex()
    }

    private fun rebuild() {
        row.removeAllViews()
        targets.clear()
        for ((i, c) in cards.withIndex()) {
            val v = CardView(context, c)
            v.setOnClickListener { index = i; act(CardKeys.Action.LAUNCH) }
            row.addView(v, LinearLayout.LayoutParams(context.dp(CARD_W_DP), -2).apply { marginEnd = context.dp(Theme.S3) })
            targets += v
        }
        hint?.let { text ->
            val pill = TextView(context).apply {
                this.text = text
                style(Theme.SP_LABEL, Theme.medium, Theme.TEXT)
                gravity = Gravity.CENTER
                val p = context.dp(Theme.S4)
                setPadding(p, context.dp(10), p, context.dp(10))
                isFocusable = true
                background = Glass.rect(context, Theme.GLASS_CHIP, 999, Theme.STROKE)
                setOnFocusChangeListener { v, f ->
                    v.background = Glass.rect(context, if (f) GlassTokens.FOCUS_FILL else Theme.GLASS_CHIP, 999, if (f) 0 else Theme.STROKE)
                    (v as TextView).setTextColor(if (f) Theme.TEXT_DARK else Theme.TEXT)
                }
                setOnClickListener { host.onOpenSetup() }
            }
            row.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_VERTICAL })
            targets += pill
        }
    }

    private fun focusIndex() {
        val v = targets.getOrNull(index) ?: return
        v.requestFocus()
        // Keep the focused card in view (the strip scrolls with the D-pad only through us).
        v.post {
            val left = v.left - context.dp(Theme.S4)
            val right = v.right + context.dp(Theme.S4) - scroller.width
            when {
                left < scroller.scrollX -> scroller.smoothScrollTo(left.coerceAtLeast(0), 0)
                right > scroller.scrollX -> scroller.smoothScrollTo(right, 0)
            }
        }
    }

    /** The host's keys while open. True = consumed; volume keys pass to the TV. */
    fun onKey(e: KeyEvent): Boolean {
        if (!isOpen) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return false
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) move(if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (down && e.repeatCount == 0) { okDown = true; okLong = false }
                else if (down && okDown && !okLong && (e.repeatCount > 0 || e.isLongPress)) { okLong = true; ok(long = true) }
                else if (!down && okDown) { okDown = false; if (!okLong) ok(long = false) }
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_X -> if (down && e.repeatCount == 0) act(CardKeys.onMenu(confirming == index))
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> if (!down) act(CardKeys.onBack(confirming == index))
        }
        return true
    }

    private fun ok(long: Boolean) {
        if (index >= cards.size) { if (!long) host.onOpenSetup(); return } // the hint pill
        act(CardKeys.onOk(long, confirming == index))
    }

    private fun move(d: Int) {
        if (targets.isEmpty()) return
        val next = (index + d).coerceIn(0, targets.size - 1)
        if (next == index) return
        cancelConfirm()
        index = next
        focusIndex()
        host.onMove()
    }

    private fun act(a: CardKeys.Action) {
        val c = cards.getOrNull(index)
        when (a) {
            CardKeys.Action.LAUNCH -> c?.let { host.onOpen(it.app, targets[index]) }
            CardKeys.Action.ASK_CLOSE -> {
                c ?: return
                val refused = CloseGuard.message(c.app.label, c.close)
                if (refused != null) { host.onCloseRefused(refused); return }
                confirming = index
                (targets[index] as? CardView)?.asking(true)
            }
            CardKeys.Action.CONFIRM_CLOSE -> {
                c ?: return
                confirming = -1
                (targets[index] as? CardView)?.asking(false)
                host.onCloseApp(c)
            }
            CardKeys.Action.CANCEL_CLOSE -> cancelConfirm()
            CardKeys.Action.LEAVE -> host.onLeave()
            CardKeys.Action.NONE -> {}
        }
    }

    private fun cancelConfirm() {
        val i = confirming
        if (i < 0) return
        confirming = -1
        (targets.getOrNull(i) as? CardView)?.asking(false)
    }

    /** One app: art on a dark tile, its name, and the playing title or the close question. */
    private inner class CardView(context: Context, val c: RecentCard) : LinearLayout(context) {
        private val art = FrameLayout(context).apply {
            background = Glass.rect(context, Theme.SURFACE, Theme.RADIUS_TILE)
            clipToOutline = true
        }
        private val name = TextView(context).apply {
            style(Theme.SP_SUB, Theme.medium)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = c.app.label
        }
        private val sub = TextView(context).apply {
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        init {
            orientation = VERTICAL
            isFocusable = true
            isClickable = true
            val p = context.dp(Theme.S2)
            setPadding(p, p, p, p)
            background = Glass.item(context, GlassTokens.RADIUS_ITEM + 4)
            addArt(c.art)
            addView(art, LayoutParams(-1, context.dp(ART_H_DP)))
            addView(name, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
            addView(sub, LayoutParams(-1, -2))
            asking(false)
            contentDescription = c.app.label
            setOnFocusChangeListener { _, f -> focused(f) }
        }

        private fun addArt(a: TileArt<Drawable>) {
            when (a) {
                is TileArt.Banner -> art.addView(image(a.d, ImageView.ScaleType.CENTER_CROP), LayoutParams(-1, -1))
                is TileArt.Adaptive -> {
                    art.addView(image(a.bg, ImageView.ScaleType.CENTER_CROP), LayoutParams(-1, -1))
                    art.addView(image(a.fg, ImageView.ScaleType.FIT_CENTER), FrameLayout.LayoutParams(context.dp(ART_H_DP), context.dp(ART_H_DP), Gravity.CENTER))
                }
                is TileArt.Legacy -> art.addView(image(a.d, ImageView.ScaleType.FIT_CENTER),
                    FrameLayout.LayoutParams(context.dp(ART_H_DP - 24), context.dp(ART_H_DP - 24), Gravity.CENTER))
                is TileArt.Letter -> {
                    art.background = Glass.rect(context, a.color, Theme.RADIUS_TILE)
                    art.addView(TextView(context).apply { style(36f, Theme.semibold); text = a.letter.toString(); gravity = Gravity.CENTER },
                        LayoutParams(-1, -1))
                }
            }
        }

        private fun image(d: Drawable, scale: ImageView.ScaleType) = ImageView(context).apply { setImageDrawable(d); scaleType = scale }

        fun asking(on: Boolean) {
            sub.text = if (on) SwitcherText.ASK else c.mediaTitle ?: ""
            sub.setTextColor(if (on) Theme.TEXT else Theme.TEXT_2)
            sub.visibility = if (sub.text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            refreshBackground(hasFocus(), on)
        }

        private fun focused(f: Boolean) {
            refreshBackground(f, confirming >= 0 && targets.getOrNull(confirming) === this)
            val s = if (f) FOCUS_SCALE else 1f
            animate().scaleX(s).scaleY(s).setDuration(Motion.ms(if (full) 160L else 110L)).start()
        }

        private fun refreshBackground(f: Boolean, asking: Boolean) {
            background = when {
                asking -> Glass.rect(context, 0x40FF6B5E, GlassTokens.RADIUS_ITEM + 4, 0xFFFF8A80.toInt(), 2)
                f -> Glass.rect(context, Theme.GLASS_ROW_FOCUS, GlassTokens.RADIUS_ITEM + 4, Theme.TEXT, 2)
                else -> Glass.item(context, GlassTokens.RADIUS_ITEM + 4)
            }
        }
    }

    private companion object {
        const val SLIDE_DP = 24
        const val SCRIM_DP = 360
        const val CARD_W_DP = 196
        const val ART_H_DP = 104
        const val FOCUS_SCALE = 1.05f
    }
}
