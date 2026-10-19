package dev.tvlauncher.nowplaying

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.RoundButton
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style

/**
 * Compact glass "Now Playing" card: 72 dp rounded album art (brand-colour tile with a note when
 * there is no art), title and artist on one line each, a thin progress bar and Prev / Play-Pause /
 * Next round buttons (each hidden when the session does not support it). No border, soft
 * translucent fill, one accent: the playing app's brand colour.
 *
 * [ambient] = the ambient variant ([NowPlayingAmbientModel]): no buttons, no progress, not
 * focusable. The progress bar ticks once per second only while the card is started, attached,
 * shown and PLAYING ([NowPlayingCardModel.shouldTick]).
 *
 * Host: `card.bind(tracker.current, tracker.currentArt)` from the tracker callbacks;
 * `card.tracker = tracker`; [start]/[stop] from onStart/onStop (after `tracker.start()`);
 * [onAction] → `tracker.send(action)`.
 */
class NowPlayingCard(
    context: Context,
    private val ambient: Boolean = false,
    private val onAction: (TransportAction) -> Unit = {}
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private fun px(dp: Int) = (dp * density + 0.5f).toInt()

    private val main = Handler(Looper.getMainLooper())
    private var item: NowPlaying? = null
    private var bitmap: Bitmap? = null
    private var started = false
    private var ticking = false

    private val artBox = FrameLayout(context).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, px(14).toFloat())
            }
        }
        clipToOutline = true
    }
    private val fallbackBg = GradientDrawable()
    private val note = ImageView(context).apply {
        setImageResource(R.drawable.ic_np_note)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val artView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val title = TextView(context).apply {
        style(if (ambient) NowPlayingAmbientModel.LINE1_SP else 17f, Theme.medium)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private val subtitle = TextView(context).apply {
        style(if (ambient) NowPlayingAmbientModel.LINE2_SP else Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }
    private val progress = ProgressLine(context)
    private val prev = RoundButton(context, R.drawable.ic_np_prev, "Previous") { onAction(TransportAction.PREV) }
    private val playPause = RoundButton(context, R.drawable.ic_np_pause, "Pause") { onAction(TransportAction.PLAY_PAUSE) }
    private val next = RoundButton(context, R.drawable.ic_np_next, "Next") { onAction(TransportAction.NEXT) }

    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            updateProgress()
            scheduleTick()
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(14), px(12), px(16), px(12))
        background = GradientDrawable().apply {
            cornerRadius = px(22).toFloat()
            setColor(if (ambient) 0x14FFFFFF else 0x1AFFFFFF)
        }
        val artDp = if (ambient) NowPlayingAmbientModel.ART_DP else NowPlayingCardModel.ART_DP
        artBox.background = fallbackBg
        val noteSize = px(artDp * 4 / 9)
        artBox.addView(note, FrameLayout.LayoutParams(noteSize, noteSize, Gravity.CENTER))
        artBox.addView(artView, FrameLayout.LayoutParams(-1, -1))
        addView(artBox, LayoutParams(px(artDp), px(artDp)))
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(title)
        texts.addView(subtitle, LayoutParams(-1, -2).apply { topMargin = px(2) })
        if (!ambient) texts.addView(progress, LayoutParams(-1, px(3)).apply { topMargin = px(10) })
        addView(texts, LayoutParams(0, -2, 1f).apply { leftMargin = px(14); rightMargin = px(8) })
        if (!ambient) {
            val b = px(NowPlayingCardModel.BUTTON_DP)
            val pad = px(9)
            listOf(prev, playPause, next).forEachIndexed { i, btn ->
                btn.setPadding(pad, pad, pad, pad)
                addView(btn, LayoutParams(b, b).apply { if (i > 0) leftMargin = px(8) })
            }
        }
        isFocusable = false
        descendantFocusability = if (ambient) FOCUS_BLOCK_DESCENDANTS else FOCUS_AFTER_DESCENDANTS
        bind(null, null)
    }

    /** Shows [n] (null hides the card) with [art] (null = brand tile). Main thread. */
    fun bind(n: NowPlaying?, art: Bitmap?) {
        item = n
        visibility = if (n == null) GONE else VISIBLE
        if (n == null) {
            setArt(null)
            stopTick()
            return
        }
        val accent = NowPlayingAccent.forPackage(n.pkg)
        fallbackBg.setColor(accent)
        progress.accent = dev.tvlauncher.ui.BrandColors.backdropAccent(accent)
        val t = NowPlayingText.title(n)
        val a = NowPlayingText.artist(n)
        title.text = t.ifEmpty { a }
        subtitle.text = if (ambient) {
            NowPlayingAmbientModel.from(n, SystemClock.elapsedRealtime(), true).line2
        } else {
            listOf(if (t.isEmpty()) "" else a, n.appLabel).filter { it.isNotBlank() }.joinToString(" · ")
        }
        setArt(art)
        if (!ambient) {
            val hadFocus = hasFocus()
            prev.visibility = if (n.canPrev) VISIBLE else GONE
            next.visibility = if (n.canNext) VISIBLE else GONE
            playPause.visibility = if (n.canPlayPause) VISIBLE else GONE
            val icon = NowPlayingCardModel.playPauseIcon(n)
            if (icon == NowPlayingCardModel.Icon.PAUSE) playPause.setIcon(R.drawable.ic_np_pause, "Pause")
            else playPause.setIcon(R.drawable.ic_np_play, "Play")
            // Keep focus inside the card when the focused button disappears.
            if (hadFocus && !hasFocus()) listOf(playPause, next, prev).firstOrNull { it.visibility == VISIBLE }?.requestFocus()
        }
        updateProgress()
        scheduleTick()
    }

    /** Swaps the art: the view lets go of the old bitmap first (it is never recycled here). */
    private fun setArt(art: Bitmap?) {
        if (art === bitmap) return
        artView.setImageDrawable(null)
        bitmap = art
        if (art != null) artView.setImageBitmap(art)
        artView.visibility = if (art != null) VISIBLE else GONE
        note.visibility = if (art != null) GONE else VISIBLE
    }

    /**
     * Optional: the tracker feeding this card. [start] then re-checks notification access
     * ([NowPlayingAccess.isGranted]) so a tracker that hit a SecurityException retries once the
     * user has granted it (e.g. on returning from Settings).
     */
    var tracker: NowPlayingTracker? = null

    fun start() {
        started = true
        tracker?.retryIfAccessGranted()
        scheduleTick()
    }

    fun stop() {
        started = false
        stopTick()
    }

    private fun visibleNow() = started && isAttachedToWindow && isShown

    private fun scheduleTick() {
        if (ambient || ticking) return
        if (!NowPlayingCardModel.shouldTick(visibleNow(), item)) return
        ticking = true
        main.postDelayed(tick, NowPlayingCardModel.TICK_MS)
    }

    private fun stopTick() {
        main.removeCallbacks(tick)
        ticking = false
    }

    private fun updateProgress() {
        val n = item ?: return
        val pos = n.positionAt(SystemClock.elapsedRealtime())
        progress.visibility = if (n.durationMs > 0) VISIBLE else INVISIBLE
        progress.fraction = ProgressMath.fraction(pos, n.durationMs)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleTick()
    }

    override fun onDetachedFromWindow() {
        stopTick()
        artView.setImageDrawable(null)
        bitmap = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) scheduleTick() else stopTick()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) scheduleTick() else stopTick()
    }

    /** 3 dp rounded track with the accent fill. */
    private class ProgressLine(context: Context) : View(context) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        var accent: Int = Theme.TEXT
            set(v) { field = v; fill.color = v; invalidate() }
        var fraction: Float = 0f
            set(v) { if (v != field) { field = v; invalidate() } }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, r, r, track)
            if (fraction > 0f) {
                rect.right = (width * fraction).coerceAtLeast(height.toFloat())
                canvas.drawRoundRect(rect, r, r, fill)
            }
        }
    }
}

/**
 * Hero chip: "♪ Title — Artist" in a small glass pill; hidden unless
 * [NowPlayingChipModel] says visible. Call [bind] from the tracker callback and once a minute
 * (a paused item disappears 10 minutes after it paused).
 */
class NowPlayingChip(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private fun px(dp: Int) = (dp * density + 0.5f).toInt()
    private val bg = GradientDrawable().apply { cornerRadius = 999f * density; setColor(Theme.GLASS_CHIP) }

    init {
        style(Theme.SP_LABEL, Theme.medium)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setPadding(px(12), px(6), px(14), px(6))
        background = bg
        visibility = GONE
    }

    fun bind(n: NowPlaying?, nowElapsed: Long = SystemClock.elapsedRealtime()) {
        val chip = NowPlayingChipModel.from(n, nowElapsed)
        visibility = if (chip.visible) VISIBLE else GONE
        text = chip.text
        contentDescription = if (chip.visible) "Now playing: " + chip.text.removePrefix(NowPlayingChipModel.NOTE).trim() else null
    }
}
