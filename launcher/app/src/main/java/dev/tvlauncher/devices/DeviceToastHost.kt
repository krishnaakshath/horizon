package dev.tvlauncher.devices

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * Transparent, non-focusable overlay for the root FrameLayout. Call [show] with each event; cards
 * appear top-right inside the overscan-safe margins, one at a time, with up to three queued
 * (coalesced per device). Never takes focus, so D-pad navigation underneath is unaffected.
 */
class DeviceToastHost(context: Context, private val mode: EffectsMode) : FrameLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val queue = ToastQueue(3)
    private var view: DeviceToastView? = null
    private var exiting = false
    private val hold = Runnable { exit() }

    init {
        isFocusable = false
        isClickable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(Theme.MARGIN_H) - context.dp(12), context.dp(Theme.MARGIN_V) - context.dp(12),
            context.dp(Theme.MARGIN_H) - context.dp(12), context.dp(Theme.MARGIN_V) - context.dp(12))
    }

    /** Main thread. Returns true for a new card, false when it was ignored or coalesced (no sound then). */
    fun show(event: DeviceEvent): Boolean {
        val prior = queue.eventFor(event.device.id)
        val offer = queue.offer(event)
        when (offer) {
            ToastQueue.Offer.IGNORED -> Unit
            ToastQueue.Offer.QUEUED -> if (view == null) next()
            ToastQueue.Offer.PREEMPT -> { main.removeCallbacks(hold); exit() }
        }
        return DeviceAnnouncePolicy.isNewCard(event, prior, offer)
    }

    private fun next() {
        val e = queue.advance() ?: return
        val v = DeviceToastView(context, mode)
        v.bind(e)
        view = v
        exiting = false
        addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        val connected = e !is DeviceEvent.Disconnected
        v.animateIn(connected) { main.postDelayed(hold, DeviceToastView.HOLD_MS) }
        v.announceForAccessibility(v.contentDescription)
    }

    private fun exit() {
        val v = view ?: return
        if (exiting) return
        exiting = true
        main.removeCallbacks(hold)
        v.animateOut {
            removeView(v)
            view = null
            exiting = false
            next()
        }
    }

    /** Drops everything immediately (e.g. onStop). */
    fun clear() {
        main.removeCallbacks(hold)
        view?.cancel()
        removeAllViews()
        view = null
        exiting = false
        while (queue.advance() != null) { }
    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }
}
