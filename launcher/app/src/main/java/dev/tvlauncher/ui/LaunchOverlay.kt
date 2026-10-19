package dev.tvlauncher.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * Brief "opening…" feedback after a tile is clicked: a soft scrim with bouncing dots. Never
 * focusable or clickable, so it cannot block input. Hidden when the activity stops (the app's
 * window is up) or after [TIMEOUT_MS], whichever comes first.
 */
class LaunchOverlay(context: Context) : FrameLayout(context) {
    private val dots = BouncingDots(context)
    private val hideRunnable = Runnable { hide() }

    override fun hasOverlappingRendering() = false

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundColor(0x66000000)
        addView(dots, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = View.GONE
    }

    fun show() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(Motion.ms(120)).start()
        postDelayed(hideRunnable, TIMEOUT_MS)
    }

    fun hide() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        visibility = View.GONE // also stops the dots' animator
    }

    private companion object {
        const val TIMEOUT_MS = 1200L
    }
}
