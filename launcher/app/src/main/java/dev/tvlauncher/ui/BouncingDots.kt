package dev.tvlauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Three dots with a staggered vertical bounce and slight scale (~900 ms cycle). One ValueAnimator
 * drives a phase; onDraw only does arithmetic (no allocation per frame). The animator runs only
 * while the view is attached and visible.
 */
class BouncingDots(context: Context, color: Int = Theme.TEXT) : View(context) {
    private val density = resources.displayMetrics.density
    private val radius = DOT_DP * density / 2f
    private val gap = GAP_DP * density
    private val lift = LIFT_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val baseAlpha = paint.alpha
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { a ->
            phase = a.animatedValue as Float
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (DOTS * radius * 2 * MAX_SCALE + (DOTS - 1) * gap).toInt() + 2
        val h = (radius * 2 * MAX_SCALE + lift).toInt() + 2
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val total = DOTS * radius * 2 + (DOTS - 1) * gap
        var cx = (width - total) / 2f + radius
        val restY = height - radius * MAX_SCALE - 1
        for (i in 0 until DOTS) {
            var local = phase - i * STAGGER
            if (local < 0f) local += 1f
            // Bounce during the first 60% of each dot's cycle, rest for the remainder.
            val b = if (local < BOUNCE_PART) sin(PI * local / BOUNCE_PART).toFloat() else 0f
            paint.alpha = (baseAlpha * (0.55f + 0.45f * b)).toInt()
            canvas.drawCircle(cx, restY - lift * b, radius * (1f + (MAX_SCALE - 1f) * b), paint)
            cx += radius * 2 + gap
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    /** For tests/diagnostics: whether the per-frame animator is running. */
    val isAnimating: Boolean get() = animator.isRunning

    private companion object {
        const val DOTS = 3
        const val DOT_DP = 9
        const val GAP_DP = 9
        const val LIFT_DP = 10
        const val MAX_SCALE = 1.2f
        const val CYCLE_MS = 900L
        const val STAGGER = 0.14f
        const val BOUNCE_PART = 0.6f
    }
}
