package dev.tvlauncher.volume.core

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Shows [VolumeBarView] in its own small window at the top right of the TV safe area
 * (48 x 27 dp), never focusable or touchable, and removes the window [HideTimer.DEFAULT_MS] after
 * the last change. [windowType] is TYPE_ACCESSIBILITY_OVERLAY in the helper (no permission
 * needed, works over every app) or TYPE_APPLICATION in a launcher activity ([context] must then
 * be that activity). Main thread only; every WindowManager call is guarded.
 */
class VolumeOverlay(
    private val context: Context,
    private val windowType: Int,
    private val face: Typeface?,
    private val motion: () -> BarMotion,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val timer = HideTimer()
    private var root: FrameLayout? = null
    private var bar: VolumeBarView? = null
    private var attached = false
    private var hiding = false

    private val hideCheck = Runnable { checkHide() }

    val isShowing: Boolean get() = attached && !hiding

    fun show(state: VolumeState) {
        try {
            val m = motion()
            val b = bar ?: VolumeBarView(context, face).also { bar = it }
            b.motion = m
            if (!attached) attach(b, m)
            if (hiding) {
                hiding = false
                b.animate().cancel()
                b.alpha = 1f
                b.translationY = 0f
            }
            b.show(state)
            timer.poke(SystemClock.uptimeMillis())
            handler.removeCallbacks(hideCheck)
            handler.postDelayed(hideCheck, timer.durationMs)
        } catch (e: Throwable) {
            detach()
        }
    }

    /** Restarts the countdown (the key was released). */
    fun keepAlive() {
        if (!attached || hiding) return
        timer.poke(SystemClock.uptimeMillis())
        handler.removeCallbacks(hideCheck)
        handler.postDelayed(hideCheck, timer.durationMs)
    }

    fun hideNow() {
        handler.removeCallbacks(hideCheck)
        timer.cancel()
        detach()
    }

    private fun attach(b: VolumeBarView, m: BarMotion) {
        val w = wm ?: return
        val d = context.resources.displayMetrics.density
        val pad = (SHADOW_ROOM_DP * d).toInt()
        val r = root ?: FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(pad, pad, pad, pad)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }.also { root = it }
        if (b.parent == null) r.addView(b, FrameLayout.LayoutParams(-2, -2))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = ((SAFE_H_DP * d).toInt() - pad).coerceAtLeast(0)
            y = ((SAFE_V_DP * d).toInt() - pad).coerceAtLeast(0)
            title = "Volume"
            windowAnimations = 0
        }
        w.addView(r, lp)
        attached = true
        if (m.showMs > 0) {
            b.alpha = 0f
            b.translationY = if (m.slide) -SLIDE_DP * d else 0f
            b.animate().alpha(1f).translationY(0f).setDuration(m.showMs)
                .setInterpolator(DecelerateInterpolator()).start()
        } else {
            b.alpha = 1f
            b.translationY = 0f
        }
    }

    private fun checkHide() {
        val now = SystemClock.uptimeMillis()
        if (!timer.due(now)) {
            handler.postDelayed(hideCheck, timer.remaining(now).coerceAtLeast(16L))
            return
        }
        timer.cancel()
        val b = bar
        val m = try { motion() } catch (e: Throwable) { BarMotion.OFF }
        if (b == null || m.hideMs == 0L) {
            detach()
            return
        }
        hiding = true
        b.animate().alpha(0f).translationY(if (m.slide) -SLIDE_DP * context.resources.displayMetrics.density else 0f)
            .setDuration(m.hideMs).setInterpolator(AccelerateInterpolator())
            .withEndAction { if (hiding) detach() }
            .start()
    }

    private fun detach() {
        hiding = false
        bar?.animate()?.cancel()
        bar?.clear()
        val r = root
        if (attached && r != null) {
            try {
                wm?.removeViewImmediate(r)
            } catch (e: Throwable) {
                // Window already gone (service unbound, activity finishing).
            }
        }
        attached = false
    }

    private companion object {
        const val SAFE_H_DP = 48
        const val SAFE_V_DP = 27
        const val SHADOW_ROOM_DP = 16
        const val SLIDE_DP = 8f
    }
}
