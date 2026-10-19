package dev.tvlauncher.volume.core

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator

/**
 * The cover's window: a [windowType] window exactly over [CoverGeometry.cover], never focusable
 * or touchable. Only a window above the TV's popup can hide it: on the TCL 32S5403AF that popup
 * is a system window of layer 27 (SYSTEM_ERROR), above every type an app may add
 * (APPLICATION_OVERLAY is layer 12); only TYPE_ACCESSIBILITY_OVERLAY (layer 31, an enabled
 * accessibility service's own windows) is higher, so the helper is the caller. It appears at once (no fade-in: a half-transparent first frame would show the
 * TV's popup through it) and stays until the TV's own popup is gone ([CoverTiming]), then fades
 * out with the launcher's motion level; while muted it shrinks to a badge over the TV's mute badge
 * (which TCL keeps up until the sound returns) instead of hiding. The window is added on the first change and then only
 * shown and hidden. Main thread only; every WindowManager call is guarded.
 */
class CoverWindow(
    private val context: Context,
    private val windowType: Int,
    private val face: Typeface?,
    private val motion: () -> BarMotion,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val timer = HideTimer(CoverTiming.HOLD_MS)
    private var view: VolumeColumnView? = null
    private var attached = false
    private var visible = false
    private var hiding = false
    private var placedFor: Point? = null
    private var last: VolumeState? = null
    private val hideCheck = Runnable { checkHide() }

    fun show(state: VolumeState) {
        try {
            val v = view ?: VolumeColumnView(context, face).also {
                it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                view = it
            }
            v.motion = try { motion() } catch (e: Throwable) { BarMotion.OFF }
            if (!attached || placedFor != screen()) {
                if (!place(v)) return
            }
            v.animate().cancel()
            v.alpha = 1f
            hiding = false
            v.compact = false
            v.show(state)
            last = state
            if (!visible) {
                v.visibility = View.VISIBLE
                visible = true
            }
            timer.poke(SystemClock.uptimeMillis())
            handler.removeCallbacks(hideCheck)
            handler.postDelayed(hideCheck, timer.durationMs)
        } catch (e: Throwable) {
            remove()
        }
    }

    /** Muted when we start listening: the TV's mute badge is already up, cover it at once. */
    fun showBadge(state: VolumeState) {
        show(state)
        timer.cancel()
        handler.removeCallbacks(hideCheck)
        view?.compact = true
    }

    fun hideNow() {
        handler.removeCallbacks(hideCheck)
        timer.cancel()
        remove()
    }

    private fun screen(): Point {
        val p = Point()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealSize(p)
        return p
    }

    /** Adds (or re-adds after a resolution change) the window at the cover rectangle. */
    private fun place(v: VolumeColumnView): Boolean {
        val w = wm ?: return false
        val size = screen()
        val panel = CoverGeometry.cover(size.x, size.y) ?: return false
        val popup = CoverGeometry.tvPopup(size.x, size.y) ?: return false
        val rect = CoverGeometry.window(size.x, size.y) ?: return false
        if (attached) remove()
        v.setGeometry(
            CoverGeometry.relative(panel, rect),
            CoverGeometry.relative(popup, rect),
            CoverGeometry.radius(size.x, size.y),
            CoverGeometry.scale(size.x, size.y),
        )
        val lp = WindowManager.LayoutParams(
            rect.width, rect.height, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
            title = "VolumeCover"
            windowAnimations = 0
        }
        v.visibility = View.INVISIBLE
        visible = false
        w.addView(v, lp)
        attached = true
        placedFor = size
        return true
    }

    private fun checkHide() {
        val now = SystemClock.uptimeMillis()
        if (!timer.due(now)) {
            handler.postDelayed(hideCheck, timer.remaining(now).coerceAtLeast(16L))
            return
        }
        timer.cancel()
        val v = view ?: return
        if (last?.muted == true) {
            // The TV keeps its mute badge up while muted: keep that square covered.
            v.compact = true
            return
        }
        val m = try { motion() } catch (e: Throwable) { BarMotion.OFF }
        if (m.hideMs == 0L) {
            conceal(v)
            return
        }
        hiding = true
        v.animate().alpha(0f).setDuration(m.hideMs).setInterpolator(AccelerateInterpolator())
            .withEndAction { if (hiding) conceal(v) }
            .start()
    }

    /** Hidden but still attached, so the next change shows without adding a window. */
    private fun conceal(v: VolumeColumnView) {
        hiding = false
        v.visibility = View.INVISIBLE
        v.alpha = 1f
        visible = false
    }

    private fun remove() {
        hiding = false
        visible = false
        val v = view
        v?.animate()?.cancel()
        v?.clear()
        if (attached && v != null) {
            try {
                wm?.removeViewImmediate(v)
            } catch (e: Throwable) {
            }
        }
        attached = false
        placedFor = null
    }
}

/** How long the cover stays (measured on the TCL 32S5403AF). */
object CoverTiming {
    /** The TV's popup closes about 3 s after the last change (its own 3000 ms timer). */
    const val TV_POPUP_MS = 3_000L

    /** We stay a little longer so the TV's popup never peeks out when we fade. */
    const val HOLD_MS = TV_POPUP_MS + 250L
}
