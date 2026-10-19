package dev.tvlauncher.browser.ui

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import dev.tvlauncher.browser.ui.Theme.style

/** Small floating pill for transient hints (mode switches, toasts) instead of system toasts. */
class Hint(context: Context) : TextView(context) {
    private val hide = Runnable { animate().alpha(0f).translationY(context.dpf(8f)).setDuration(250).start() }

    init {
        style(14f, Theme.medium)
        gravity = Gravity.CENTER
        val h = context.dp(20); val v = context.dp(11)
        setPadding(h, v, h, v)
        background = Theme.rounded(context, 0xF21D2230.toInt(), Theme.RADIUS_PILL, Theme.STROKE, 1)
        elevation = context.dpf(6f)
        alpha = 0f
    }

    fun show(text: CharSequence, ms: Long = 2600) {
        removeCallbacks(hide)
        this.text = text
        if (alpha < 0.05f) translationY = context.dpf(8f)
        animate().cancel()
        animate().alpha(1f).translationY(0f).setDuration(200).start()
        postDelayed(hide, ms)
    }
}
