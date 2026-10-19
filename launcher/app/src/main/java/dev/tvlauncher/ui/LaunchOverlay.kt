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
import dev.tvlauncher.launch.LaunchMotion

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
    private val loader = HorizonLoader(context)
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
    fun play(plan: LaunchMotion.Plan, accent: Int, art: TileArt<Drawable>?, from: Rect?, onCovered: () -> Unit) {
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
        expand.begin(BrandColors.blend(Theme.SURFACE, accent, plan.washStrength), start)
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
                    // The app may take a while to draw: the loader under the logo says "still opening".
                    setLoaderOffset(context.dp(LOGO_H_DP / 2 + 36))
                    loader.visibility = View.VISIBLE
                    run?.invoke()
                }
            })
            start()
        }
        postDelayed(hideRunnable, plan.expandMs + TIMEOUT_AFTER_MOTION_MS)
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
        private val from = RectF()
        private val centre = RectF()
        private val now = RectF()
        private val tileRadius = context.dp(Theme.RADIUS_TILE).toFloat()
        private var color = 0
        var progress = 0f
            set(v) { field = v; invalidate() }

        fun begin(c: Int, start: RectF?) {
            color = c
            if (start != null) from.set(start) else from.setEmpty()
            progress = 0f
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val s = if (from.isEmpty) centre.apply { set(w * 0.4f, h * 0.4f, w * 0.6f, h * 0.6f) } else from
            val t = progress
            now.set(s.left * (1 - t), s.top * (1 - t), s.right + (w - s.right) * t, s.bottom + (h - s.bottom) * t)
            val r = tileRadius * (1 - t)
            paint.color = color
            paint.alpha = (START_ALPHA + (0xFF - START_ALPHA) * t).toInt()
            canvas.drawRoundRect(now, r, r, paint)
        }

        private companion object {
            const val START_ALPHA = 0xD0
        }
    }

    private companion object {
        const val TIMEOUT_MS = 1200L
        /** After the motion: long enough for a slow app to draw before home shows again. */
        const val TIMEOUT_AFTER_MOTION_MS = 2500L
        const val SCRIM = 0x66000000
        const val LOGO_W_DP = 240
        const val LOGO_H_DP = 135
    }
}
