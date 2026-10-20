package dev.tvlauncher.splash

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.ui.SplashLook
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Start-up style "Minimal fade": the M02 mark fades in with a slight settle (and, on FULL, the
 * letter-spaced MINTAKA caption below), then the home rises. No glow, no shapes: the calmest style.
 */
class MinimalSplash(
    context: Context,
    full: Boolean,
    @Suppress("UNUSED_PARAMETER") accent: Int,
    timeline: StartupTimelines.Timeline
) : TimelineSplash(context, full, timeline) {
    private val mark = TextView(context).apply {
        text = Brand.SHORT
        style(if (full) SplashLook.MARK_SP else SplashLook.MARK_SP_LITE, Theme.brand)
        letterSpacing = 0.04f
        alpha = 0f
        scaleX = 1.04f; scaleY = 1.04f
    }
    private val caption: TextView? = if (!full) null else TextView(context).apply {
        text = Brand.CAPTION
        style(SplashLook.CAPTION_SP, Theme.brand, Theme.TEXT_2)
        letterSpacing = SplashLook.CAPTION_TRACKING
        alpha = 0f
    }

    init {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(mark, LinearLayout.LayoutParams(-2, -2))
            caption?.let { addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(Theme.S4) }) }
        }
        addView(group, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun playIntro(s: StartupTimelines.Step) {
        when (s.part) {
            StartupTimelines.Part.MARK -> mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(decel).at(s)
            StartupTimelines.Part.CAPTION -> caption?.animate()?.alpha(1f)?.setInterpolator(decel)?.at(s)
            else -> Unit
        }
    }
}
