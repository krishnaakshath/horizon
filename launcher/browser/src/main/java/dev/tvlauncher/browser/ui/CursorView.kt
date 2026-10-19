package dev.tvlauncher.browser.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import dev.tvlauncher.browser.Perf
import dev.tvlauncher.browser.cursor.CursorRenderer
import dev.tvlauncher.browser.cursor.CursorSettings
import dev.tvlauncher.browser.cursor.CursorState

/**
 * Virtual-mouse overlay. Never takes input. The drawn position glides toward the target; state
 * changes morph within 120 ms with a small pop; clicks ripple; long-press fills an arc; edge
 * scrolling shows chevrons at the screen edge; an optional 6-point trail (FULL mode only);
 * auto-hides after the configured idle time.
 */
class CursorView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    val renderer = CursorRenderer(density)
    private var tx = -1f
    private var ty = -1f
    private var dx = -1f
    private var dy = -1f
    var state = CursorState.DEFAULT; private set

    private val from = FloatArray(5)
    private val to = FloatArray(5)
    private val morph = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120
        interpolator = DecelerateInterpolator()
        addUpdateListener { a ->
            val t = a.animatedValue as Float
            renderer.linkAmt = lerp(0, t); renderer.textAmt = lerp(1, t); renderer.busyAmt = lerp(2, t)
            renderer.disabledAmt = lerp(3, t); renderer.scrollAmt = lerp(4, t)
            renderer.pop = 1f + 0.14f * kotlin.math.sin(Math.PI * t).toFloat()
            invalidate()
        }
    }
    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { renderer.spin = it.animatedValue as Float; invalidateCursor() }
    }
    private val rippleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = DecelerateInterpolator()
        addUpdateListener { renderer.ripple = it.animatedValue as Float; invalidateCursor() }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { renderer.ripple = -1f; invalidate() }
        })
    }
    private val longAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = LinearInterpolator()
        addUpdateListener { renderer.longProgress = it.animatedValue as Float; invalidateCursor() }
    }

    // Edge chevrons (screen edge feedback while edge-scrolling).
    private var edgeDir = 0
    private var edgeAlpha = 0f
    private val edgeAnim = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 600
        addUpdateListener { edgeAlpha = it.animatedValue as Float; invalidate() }
    }
    private val chevron = Path()
    private val chevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Theme.TEXT
    }
    private val chevBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99151922.toInt() }

    // Trail: 6 pre-allocated points.
    private val trailX = FloatArray(6)
    private val trailY = FloatArray(6)
    private var trailN = 0
    private var trailHead = 0
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val fade = Runnable { animate().alpha(0f).setDuration(200).start() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val c = 7f * density
        chevron.moveTo(-c, c * 0.5f); chevron.lineTo(0f, -c * 0.5f); chevron.lineTo(c, c * 0.5f)
    }

    fun applySettings(s: CursorSettings) {
        renderer.apply(s)
        trailN = 0
        rippleAnim.duration = if (Perf.lite) 180 else 300
        wake()
        invalidate()
    }

    fun moveTo(x: Float, y: Float, animate: Boolean = true) {
        if (trailEnabled() && dx >= 0) {
            trailX[trailHead] = dx; trailY[trailHead] = dy
            trailHead = (trailHead + 1) % trailX.size
            if (trailN < trailX.size) trailN++
        }
        tx = x; ty = y
        if (!animate || dx < 0 || Perf.animationsOff) { dx = x; dy = y }
        wake()
        postInvalidateOnAnimation()
    }

    private fun trailEnabled() = renderer.settings.trail && !Perf.lite && !Perf.animationsOff

    fun setState(s: CursorState) {
        if (s == state) return
        state = s
        from[0] = renderer.linkAmt; from[1] = renderer.textAmt; from[2] = renderer.busyAmt
        from[3] = renderer.disabledAmt; from[4] = renderer.scrollAmt
        to.fill(0f)
        when (s) {
            CursorState.LINK -> to[0] = 1f
            CursorState.TEXT -> to[1] = 1f
            CursorState.BUSY -> to[2] = 1f
            CursorState.DISABLED -> to[3] = 1f
            CursorState.SCROLL -> to[4] = 1f
            CursorState.DEFAULT -> Unit
        }
        morph.cancel(); morph.start()
        if (s == CursorState.BUSY) { if (!spinner.isStarted) spinner.start() } else spinner.cancel()
    }

    private fun lerp(i: Int, t: Float) = from[i] + (to[i] - from[i]) * t

    fun press(down: Boolean) {
        renderer.pressAmt = if (down) 1f else 0f
        wake()
        invalidate()
    }

    fun click() {
        if (Perf.animationsOff) return
        rippleAnim.cancel(); rippleAnim.start()
    }

    fun longPressStart(ms: Long) {
        if (Perf.animationsOff) return
        longAnim.cancel(); longAnim.duration = ms; longAnim.start()
    }

    fun longPressEnd(fired: Boolean) {
        longAnim.cancel()
        renderer.longProgress = 0f
        if (fired) click() else invalidate()
    }

    fun edge(dir: Int) {
        edgeDir = dir
        edgeAnim.cancel(); edgeAnim.start()
    }

    fun wake() {
        removeCallbacks(fade)
        animate().cancel()
        alpha = 1f
        val ms = renderer.settings.autoHideMs
        if (ms > 0) postDelayed(fade, ms)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fade)
        morph.cancel(); spinner.cancel(); rippleAnim.cancel(); longAnim.cancel(); edgeAnim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) spinner.cancel() else if (state == CursorState.BUSY && !spinner.isStarted) spinner.start()
    }

    private fun invalidateCursor() = invalidate()

    override fun onDraw(canvas: Canvas) {
        if (edgeAlpha > 0.01f && edgeDir != 0) {
            val cx = width / 2f
            val cy = if (edgeDir < 0) 22f * density else height - 22f * density
            chevBg.alpha = (0x99 * edgeAlpha).toInt()
            canvas.drawCircle(cx, cy, 16f * density, chevBg)
            chevPaint.alpha = (255 * edgeAlpha).toInt()
            canvas.save(); canvas.translate(cx, cy); if (edgeDir > 0) canvas.scale(1f, -1f)
            canvas.drawPath(chevron, chevPaint); canvas.restore()
        }
        if (tx < 0) return
        dx += (tx - dx) * 0.5f
        dy += (ty - dy) * 0.5f
        if (kotlin.math.abs(tx - dx) < 0.5f && kotlin.math.abs(ty - dy) < 0.5f) {
            dx = tx; dy = ty
            if (trailN > 0) { trailN--; postInvalidateOnAnimation() }
        } else postInvalidateOnAnimation()
        if (trailEnabled() && trailN > 0) {
            val col = renderer.settings.color
            for (i in 0 until trailN) {
                val idx = (trailHead - 1 - i + trailX.size) % trailX.size
                val k = 1f - (i + 1f) / (trailX.size + 1f)
                trailPaint.color = (col and 0x00FFFFFF) or ((90 * k).toInt() shl 24)
                canvas.drawCircle(trailX[idx], trailY[idx], 3.5f * density * k, trailPaint)
            }
        }
        renderer.draw(canvas, dx, dy, 1f)
    }
}
