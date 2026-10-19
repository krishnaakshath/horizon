package dev.tvlauncher.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Full-screen brand-tinted backdrop. One diagonal gradient (accent at top-left fading to the
 * near-black base) whose colors crossfade on focus changes, plus a static bottom scrim that keeps
 * the dock area calm. Nothing is allocated per frame: the color arrays and animator are reused.
 */
class BackdropView(context: Context, durationMs: Long) : View(context) {
    private val evaluator = ArgbEvaluator()
    private val from = IntArray(STOPS)
    private val to = IntArray(STOPS)
    private val now = IntArray(STOPS) { Theme.BASE }
    private val glow = GradientDrawable(GradientDrawable.Orientation.TL_BR, now.copyOf())
    private val scrim = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt())
    )
    private var accent = 0
    private var hasAccent = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { a ->
            val t = a.animatedFraction
            for (i in 0 until STOPS) now[i] = evaluator.evaluate(t, from[i], to[i]) as Int
            glow.colors = now
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
    }

    /** Color the hero text sits on (top-left of the gradient). */
    val heroColor: Int get() = to[0]

    fun setAccent(color: Int, animate: Boolean = true) {
        if (hasAccent && color == accent) return
        hasAccent = true
        accent = color
        val a = BrandColors.backdropAccent(color)
        System.arraycopy(now, 0, from, 0, STOPS)
        to[0] = BrandColors.blend(Theme.BASE, a, 0.62f)
        to[1] = BrandColors.blend(Theme.BASE, a, 0.26f)
        to[2] = Theme.BASE
        animator.cancel()
        if (animate && animator.duration > 0) {
            animator.start()
        } else {
            System.arraycopy(to, 0, now, 0, STOPS)
            glow.colors = now
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        glow.setBounds(0, 0, w, h)
        scrim.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        glow.draw(canvas)
        scrim.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STOPS = 3
    }
}
