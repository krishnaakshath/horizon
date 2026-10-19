package dev.tvlauncher.appsettings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme

/** A drawn pill switch. Not focusable itself: its [SettingsRow] owns focus and key handling. */
internal class ToggleSwitch(context: Context, private var accent: Int, private val durationMs: Long) : View(context) {
    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT }
    private val rect = RectF()
    private var t = 0f
    private var animator: ValueAnimator? = null

    var checked: Boolean = false
        private set

    fun setChecked(value: Boolean, animate: Boolean) {
        checked = value
        val target = if (value) 1f else 0f
        animator?.cancel()
        if (!animate || durationMs <= 0L) {
            t = target; invalidate(); return
        }
        animator = ValueAnimator.ofFloat(t, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun setAccent(color: Int) {
        accent = color; invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((W_DP * density).toInt(), (H_DP * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        track.color = BrandColors.blend(OFF_TRACK, accent, t)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, track)
        val inset = 3f * density
        val r = h / 2f - inset
        val cx = inset + r + (width - 2 * (inset + r)) * t
        canvas.drawCircle(cx, h / 2f, r, thumb)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val W_DP = 48
        const val H_DP = 28
        const val OFF_TRACK = Theme.TOGGLE_OFF
    }
}
