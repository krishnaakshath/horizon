package dev.tvlauncher.browser.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The H02 loading motif (same as the launcher's HorizonLoader): a small sun-disc gliding along a thin horizon line and back (one
 * [ValueAnimator] phase, [HorizonLoaderMath] for the geometry; no allocation per frame, no
 * hardware layer). Runs only while attached and visible, and never when animations are off; the disc
 * then rests at the centre of the line.
 */
class HorizonLoader(context: Context, color: Int = Theme.TEXT) : View(context) {
    private val density = resources.displayMetrics.density
    private val lineW = LINE_DP * density
    private val disc = DISC_DP * density / 2f
    private val stroke = STROKE_DP * density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        alpha = LINE_ALPHA
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = HALO_ALPHA }
    private var phase = 0.5f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = HorizonLoaderMath.CYCLE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { a ->
            phase = a.animatedValue as Float
            invalidate()
        }
    }

    override fun hasOverlappingRendering() = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (lineW + disc * 4).toInt() + 2
        val h = (disc * 4).toInt() + 2
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val left = (width - lineW) / 2f
        val y = height / 2f
        canvas.drawLine(left, y, left + lineW, y, line)
        val p = if (animator.isRunning) phase else 0.5f
        val cx = left + lineW * HorizonLoaderMath.position(p)
        val fade = HorizonLoaderMath.brightness(p)
        halo.alpha = (HALO_ALPHA * fade).toInt()
        canvas.drawCircle(cx, y, disc * 1.9f, halo)
        sun.alpha = (255 * (0.55f + 0.45f * fade)).toInt()
        canvas.drawCircle(cx, y, disc, sun)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow && !dev.tvlauncher.browser.Perf.animationsOff) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown && !dev.tvlauncher.browser.Perf.animationsOff && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    /** For tests/diagnostics: whether the per-frame animator is running. */
    val isAnimating: Boolean get() = animator.isRunning

    private companion object {
        const val LINE_DP = 44
        const val DISC_DP = 8
        const val STROKE_DP = 1.5f
        const val LINE_ALPHA = 0x55
        const val HALO_ALPHA = 0x30
    }
}

/** Pure geometry of [HorizonLoader] (JVM-testable). Phase 0..1 is one there-and-back cycle. */
object HorizonLoaderMath {
    const val CYCLE_MS = 1400L

    /** Disc position along the line, 0 (left) .. 1 (right): eased out and back, continuous at the wrap. */
    fun position(phase: Float): Float {
        val p = phase.coerceIn(0f, 1f)
        val t = if (p < 0.5f) p * 2f else (1f - p) * 2f
        return t * t * (3f - 2f * t) // smoothstep: slows at both ends of the line
    }

    /** The disc glows brightest mid-line and dims near the ends ("rising" and "setting"). */
    fun brightness(phase: Float): Float {
        val x = position(phase)
        return 1f - (2f * x - 1f) * (2f * x - 1f)
    }
}
