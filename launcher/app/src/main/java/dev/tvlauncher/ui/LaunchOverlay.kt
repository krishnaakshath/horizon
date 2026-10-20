package dev.tvlauncher.ui

import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import dev.tvlauncher.launch.LaunchGeometry
import dev.tvlauncher.launch.LaunchMotion
import dev.tvlauncher.launch.LaunchWatch
import dev.tvlauncher.launch.LoaderViews
import dev.tvlauncher.splash.LaunchStyle
import dev.tvlauncher.splash.LoaderStyle

/**
 * Brief "opening…" feedback after a tile is clicked. Never focusable or clickable, so it cannot
 * block input. Hidden when the activity stops (the app's window is up) or after a timeout,
 * whichever comes first.
 *
 * [show] is the plain version: a soft scrim with the brand loader (a sun-disc on a horizon line).
 * [play] is the per-app launch motion ([LaunchMotion]): the tile grows into a full-screen wash of
 * the app's brand colour while its logo fades in, then `onCovered` hands over to the app.
 */
class LaunchOverlay(context: Context) : FrameLayout(context) {
    private var loader: View = HorizonLoader(context)
    private var loaderStyle = LoaderStyle.SUN_LINE
    /** The app-launch splash: loader after [LaunchWatch.LOADER_DELAY_MS], hard dismissal at 4 s. */
    private val watch = LaunchWatch()
    private val watchRunnable = Runnable { onWatchTick() }
    private val expand = ExpandView(context)
    private val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val hideRunnable = Runnable { hide() }
    private var animator: ValueAnimator? = null
    private var pending: (() -> Unit)? = null

    override fun hasOverlappingRendering() = false

    /** True while the launch motion runs (a second launch waits for it). */
    val isPlaying: Boolean get() = animator?.isRunning == true

    init {
        auditTag(AuditTags.FULLBLEED, AuditTags.IGNORE) // transient
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(expand, LayoutParams(-1, -1))
        addView(logo, LayoutParams(context.dp(LOGO_W_DP), context.dp(LOGO_H_DP), Gravity.CENTER))
        addView(loader, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        expand.visibility = View.GONE
        logo.visibility = View.GONE
        visibility = View.GONE
    }

    /** Settings > Appearance > Start-up and loading: which loader to show. */
    fun setLoaderStyle(style: LoaderStyle) {
        if (style == loaderStyle) return
        loaderStyle = style
        val i = indexOfChild(loader)
        val lp = loader.layoutParams
        removeView(loader)
        loader = LoaderViews.create(context, style).also { it.visibility = loader.visibility }
        addView(loader, i, lp)
    }

    fun show() {
        stop()
        expand.visibility = View.GONE
        logo.visibility = View.GONE
        loader.visibility = View.VISIBLE
        setLoaderOffset(0)
        setBackgroundColor(SCRIM)
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(Motion.ms(120)).start()
        postDelayed(hideRunnable, TIMEOUT_MS)
    }

    /**
     * The launch motion from `from` (window coordinates of the tile; null = from the centre) in
     * `accent`; `onCovered` runs once the screen is covered (start the app then). With a plan
     * that is not animated, falls back to [show] and calls `onCovered` at once.
     */
    fun play(
        plan: LaunchMotion.Plan, accent: Int, art: TileArt<Drawable>?, from: Rect?,
        style: LaunchStyle = LaunchStyle.BRAND_EXPAND, onCovered: () -> Unit
    ) {
        if (!plan.animated) {
            show()
            onCovered()
            return
        }
        stop()
        setBackgroundColor(0)
        alpha = 1f
        visibility = View.VISIBLE
        loader.visibility = View.GONE
        val d = logoOf(art)
        logo.setImageDrawable(d)
        logo.visibility = if (d != null) View.VISIBLE else View.GONE
        logo.alpha = 0f
        val here = IntArray(2).also { getLocationInWindow(it) }
        val start = from?.let { RectF(it) }?.apply { offset(-here[0].toFloat(), -here[1].toFloat()) }
        expand.begin(BrandColors.blend(Theme.SURFACE, accent, plan.washStrength), start, style)
        watch.begin(android.os.SystemClock.uptimeMillis())
        expand.visibility = View.VISIBLE
        pending = onCovered
        val logoFrom = plan.logoDelayMs.toFloat() / plan.expandMs
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = plan.expandMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { a ->
                expand.progress = a.animatedValue as Float
                val f = a.animatedFraction
                logo.alpha = if (f <= logoFrom) 0f else ((f - logoFrom) / (1f - logoFrom)).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    val run = pending
                    pending = null
                    // The app may take a while to draw: past LOADER_DELAY_MS the loader under the
                    // logo says "still opening" (LaunchWatch); fast apps never show it.
                    setLoaderOffset(context.dp(LOGO_H_DP / 2 + 36))
                    act(watch.motionEnded(android.os.SystemClock.uptimeMillis()))
                    run?.invoke()
                }
            })
            start()
        }
        postDelayed(hideRunnable, LaunchWatch.HARD_TIMEOUT_MS) // backstop: never a stuck overlay
    }

    private fun onWatchTick() = act(watch.tick(android.os.SystemClock.uptimeMillis()))

    private fun act(a: LaunchWatch.Action) {
        when (a) {
            LaunchWatch.Action.SHOW_LOADER -> loader.visibility = View.VISIBLE
            LaunchWatch.Action.DISMISS -> { hide(); return }
            LaunchWatch.Action.NONE -> Unit
        }
        removeCallbacks(watchRunnable)
        watch.nextAt()?.let { at -> postDelayed(watchRunnable, (at - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L)) }
    }

    private fun setLoaderOffset(px: Int) {
        val lp = loader.layoutParams as LayoutParams
        if (lp.topMargin != px) { lp.topMargin = px; loader.layoutParams = lp }
    }

    private fun logoOf(art: TileArt<Drawable>?): Drawable? = when (art) {
        is TileArt.Banner -> art.d
        is TileArt.Adaptive -> LayerDrawable(arrayOf(art.bg, art.fg))
        is TileArt.Legacy -> art.d
        else -> null
    }

    private fun stop() {
        removeCallbacks(hideRunnable)
        removeCallbacks(watchRunnable)
        watch.stop()
        animate().cancel()
        pending = null
        animator?.cancel()
        animator = null
    }

    fun hide() {
        stop()
        logo.setImageDrawable(null)
        visibility = View.GONE // also stops the loader's animator
    }

    /** The growing card: a rounded rectangle from the tile's bounds to the whole screen. */
    private class ExpandView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val from = FloatArray(4)
        private var hasFrom = false
        private val frame = FloatArray(LaunchGeometry.OUT_SIZE)
        private val now = RectF()
        private var style = LaunchStyle.BRAND_EXPAND
        private val tileRadius = context.dp(Theme.RADIUS_TILE).toFloat()
        private var color = 0
        var progress = 0f
            set(v) { field = v; invalidate() }

        fun begin(c: Int, start: RectF?, s: LaunchStyle) {
            color = c
            style = s
            hasFrom = start != null && !start.isEmpty
            if (start != null) { from[0] = start.left; from[1] = start.top; from[2] = start.right; from[3] = start.bottom }
            progress = 0f
        }

        override fun onDraw(canvas: Canvas) {
            LaunchGeometry.frame(style, progress, if (hasFrom) from else null, width.toFloat(), height.toFloat(), frame)
            now.set(frame[0], frame[1], frame[2], frame[3])
            val r = tileRadius * frame[5]
            paint.color = color
            paint.alpha = (0xFF * frame[4]).toInt()
            canvas.drawRoundRect(now, r, r, paint)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 1200L
        const val SCRIM = 0x66000000
        const val LOGO_W_DP = 240
        const val LOGO_H_DP = 135
    }
}
