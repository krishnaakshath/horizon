package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * Virtual mouse pointer overlay (16dp disc with a dark rim and soft halo). Never takes touches.
 * The drawn position eases toward the target so stepped D-pad moves glide; it fades after a
 * few idle seconds (so it doesn't sit on top of video) and reappears on the next move.
 */
class CursorView(context: Context) : View(context) {
    private val r = context.dpf(8f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.TEXT }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(2f); color = 0xCC0B0D12.toInt()
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x330B0D12 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(2f); color = Theme.ACCENT
    }
    private var tx = -1f
    private var ty = -1f
    private var dx = -1f
    private var dy = -1f
    private var pressed = false
    private val fade = Runnable { animate().alpha(0f).setDuration(400).start() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun moveTo(x: Float, y: Float, animate: Boolean = true) {
        tx = x; ty = y
        if (!animate || dx < 0) { dx = x; dy = y }
        wake()
        postInvalidateOnAnimation()
    }

    fun setPressed2(p: Boolean) {
        pressed = p
        wake()
        invalidate()
    }

    fun wake() {
        removeCallbacks(fade)
        animate().cancel()
        alpha = 1f
        postDelayed(fade, IDLE_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fade)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (tx < 0) return
        dx += (tx - dx) * 0.55f
        dy += (ty - dy) * 0.55f
        if (kotlin.math.abs(tx - dx) < 0.5f && kotlin.math.abs(ty - dy) < 0.5f) { dx = tx; dy = ty } else postInvalidateOnAnimation()
        val s = if (pressed) 0.78f else 1f
        canvas.drawCircle(dx, dy, r * 1.55f * s, halo)
        canvas.drawCircle(dx, dy, r * s, fill)
        canvas.drawCircle(dx, dy, r * s, rim)
        if (pressed) canvas.drawCircle(dx, dy, r * 1.9f, ring)
    }

    private companion object { const val IDLE_MS = 5000L }
}
