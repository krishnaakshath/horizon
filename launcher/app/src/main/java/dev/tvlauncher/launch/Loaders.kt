package dev.tvlauncher.launch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import dev.tvlauncher.splash.LoaderStyle
import dev.tvlauncher.ui.HorizonLoader
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme

/** The loader view for [style] (Settings > Appearance > Start-up and loading). */
object LoaderViews {
    fun create(context: Context, style: LoaderStyle, color: Int = Theme.TEXT): View = when (style) {
        LoaderStyle.SUN_LINE -> HorizonLoader(context, color)
        LoaderStyle.PULSE_RING -> PulseRingLoader(context, color)
        LoaderStyle.DOTS -> DotsLoader(context, color)
    }
}

/**
 * Shared plumbing of the loaders: one infinite [ValueAnimator] phase that runs only while attached,
 * shown and [Motion.enabled]; otherwise the loader rests at [restPhase]. Paints are made once; no
 * allocation per frame; no hardware layer.
 */
abstract class PhaseLoader(context: Context, cycleMs: Long) : View(context) {
    protected val density = resources.displayMetrics.density
    protected var phase = 0f
        private set
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = cycleMs
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { a ->
            phase = a.animatedFraction
            invalidate()
        }
    }

    protected abstract val restPhase: Float
    protected val drawPhase: Float get() = if (animator.isRunning) phase else restPhase

    override fun hasOverlappingRendering() = false

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow && Motion.enabled) {
            if (!animator.isStarted) animator.start()
        } else animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown && Motion.enabled && !animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    val isAnimating: Boolean get() = animator.isRunning
}

/** "Pulse ring": a small disc with a ring that breathes out from it and fades. */
class PulseRingLoader(context: Context, color: Int = Theme.TEXT) : PhaseLoader(context, PulseRingMath.CYCLE_MS) {
    private val coreR = PulseRingMath.CORE_DP * density / 2f
    private val maxR = PulseRingMath.RING_DP * density / 2f
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    override val restPhase = 0.35f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (maxR * 2 + 4 * density).toInt()
        setMeasuredDimension(resolveSize(s, widthMeasureSpec), resolveSize(s, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val p = drawPhase
        val cx = width / 2f; val cy = height / 2f
        ring.alpha = (255 * PulseRingMath.ringAlpha(p)).toInt()
        canvas.drawCircle(cx, cy, coreR + (maxR - coreR) * PulseRingMath.ringRadius(p), ring)
        canvas.drawCircle(cx, cy, coreR, core)
    }
}

/** "Dots": three dots brightening in turn, like a small wave along a line. */
class DotsLoader(context: Context, color: Int = Theme.TEXT) : PhaseLoader(context, DotsMath.CYCLE_MS) {
    private val r = DotsMath.DOT_DP * density / 2f
    private val gap = DotsMath.GAP_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    override val restPhase = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (r * 2 * DotsMath.COUNT + gap * (DotsMath.COUNT - 1) + 4 * density).toInt()
        val h = (r * 4).toInt() + 2
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val p = drawPhase
        val total = r * 2 * DotsMath.COUNT + gap * (DotsMath.COUNT - 1)
        var x = (width - total) / 2f + r
        val y = height / 2f
        for (i in 0 until DotsMath.COUNT) {
            val lift = DotsMath.lift(p, i)
            paint.alpha = (255 * (0.35f + 0.65f * lift)).toInt()
            canvas.drawCircle(x, y - lift * r, r, paint)
            x += r * 2 + gap
        }
    }
}

/** Pure timing of [PulseRingLoader] (phase 0..1 is one breath). */
object PulseRingMath {
    const val CYCLE_MS = 1300L
    const val CORE_DP = 8
    const val RING_DP = 30

    /** 0 (at the core) .. 1 (fully out), eased out. */
    fun ringRadius(phase: Float): Float {
        val p = phase.coerceIn(0f, 1f)
        return 1f - (1f - p) * (1f - p)
    }

    /** Opaque-ish as it leaves the core, gone when fully out (so the wrap is invisible). */
    fun ringAlpha(phase: Float): Float = 0.8f * (1f - phase.coerceIn(0f, 1f))
}

/** Pure timing of [DotsLoader]. */
object DotsMath {
    const val CYCLE_MS = 1200L
    const val COUNT = 3
    const val DOT_DP = 7
    const val GAP_DP = 7

    /** How lit dot [i] is at [phase]: a bump travelling left to right, 0..1, continuous at the wrap. */
    fun lift(phase: Float, i: Int): Float {
        val centre = (i + 0.5f) / COUNT
        var d = kotlin.math.abs(phase.coerceIn(0f, 1f) - centre)
        if (d > 0.5f) d = 1f - d
        val x = (1f - d * COUNT).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
