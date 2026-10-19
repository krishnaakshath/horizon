package dev.tvlauncher.browser.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Thin page-load progress line; glides between values and fades out at 100%. */
class ProgressLine(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shown = 0f
    private var anim: ValueAnimator? = null

    init { alpha = 0f }

    fun setProgress(p: Int) {
        val target = p.coerceIn(0, 100) / 100f
        if (p in 1..99 && alpha < 1f) {
            animate().cancel(); alpha = 1f
        }
        if (target < shown) shown = 0f
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
        if (p >= 100) animate().alpha(0f).setStartDelay(250).setDuration(250).withEndAction { shown = 0f }.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Theme.ACCENT, Theme.TEXT, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width * shown, height.toFloat(), r, r, paint)
    }
}
