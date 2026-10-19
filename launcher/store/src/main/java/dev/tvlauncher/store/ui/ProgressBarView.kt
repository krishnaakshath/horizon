package dev.tvlauncher.store.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Premium progress bar: rounded track with a mint→blue gradient fill and a soft leading glow.
 * progress < 0 shows an indeterminate sweeping segment. No blur (LITE-friendly).
 */
class ProgressBarView(context: Context) : View(context) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Kit.SURFACE_2 }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x337CF2C8 }
    private val rect = RectF()
    private var shaderWidth = -1
    private var phase = 0f
    private var animator: ValueAnimator? = null

    /** 0..1, or negative for indeterminate. */
    var progress: Float = -1f
        set(value) {
            field = value
            updateAnimation()
            invalidate()
        }

    private fun updateAnimation() {
        if (progress < 0 && isAttachedToWindow) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1400
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
        } else {
            animator?.cancel()
            animator = null
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(12))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        if (shaderWidth != width) {
            fill.shader = LinearGradient(0f, 0f, w, 0f, Kit.ACCENT, Kit.ACCENT_2, Shader.TileMode.CLAMP)
            shaderWidth = width
        }
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, track)
        if (progress >= 0) {
            val p = progress.coerceIn(0f, 1f)
            val end = maxOf(h, w * p)
            rect.set(0f, 0f, end, h)
            canvas.drawRoundRect(rect, r, r, fill)
            if (p in 0.001f..0.999f) canvas.drawCircle(end - r, r, r * 1.6f, glow)
        } else {
            val seg = w * 0.28f
            val start = (w + seg) * phase - seg
            rect.set(maxOf(0f, start), 0f, minOf(w, start + seg), h)
            if (rect.width() > 0) canvas.drawRoundRect(rect, r, r, fill)
        }
    }
}
