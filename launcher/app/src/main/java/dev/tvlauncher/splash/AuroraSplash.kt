package dev.tvlauncher.splash

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.ui.SplashLook
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Start-up style "Aurora sweep": a soft band of the accent and a neighbouring hue sweeps across the
 * screen once, the H02 mark resolving as it passes, then the home rises. The band is one static
 * GradientDrawable moved by translationX (no per-frame allocation, no shader animation, no layer).
 */
class AuroraSplash(
    context: Context,
    full: Boolean,
    accent: Int,
    timeline: StartupTimelines.Timeline
) : TimelineSplash(context, full, timeline) {
    private val screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)

    private val band = View(context).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            AuroraLook.colors(accent, full)
        )
        translationX = -screenW.toFloat()
    }

    private val mark = TextView(context).apply {
        text = Brand.SHORT
        style(if (full) SplashLook.MARK_SP else SplashLook.MARK_SP_LITE, Theme.brand)
        letterSpacing = 0.04f
        alpha = 0f
        translationY = context.dp(10).toFloat()
    }

    private val caption: TextView? = if (!full) null else TextView(context).apply {
        text = Brand.CAPTION
        style(SplashLook.CAPTION_SP, Theme.brand, Theme.TEXT_2)
        letterSpacing = SplashLook.CAPTION_TRACKING
        alpha = 0f
    }

    init {
        addView(band, LayoutParams(screenW, LayoutParams.MATCH_PARENT))
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
            StartupTimelines.Part.SWEEP -> band.animate().translationX(screenW.toFloat()).setInterpolator(decel).at(s)
            StartupTimelines.Part.MARK -> mark.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s)
            StartupTimelines.Part.CAPTION -> caption?.animate()?.alpha(1f)?.setInterpolator(decel)?.at(s)
            else -> Unit
        }
    }
}

/** Aurora colours (JVM-testable). */
object AuroraLook {
    const val FULL_ALPHA = 0x73
    const val LITE_ALPHA = 0x4D

    /** Transparent edges, the accent, then the accent's hue turned toward violet; never opaque. */
    fun colors(accent: Int, full: Boolean): IntArray {
        val a = if (full) FULL_ALPHA else LITE_ALPHA
        val rgb = accent and 0x00FFFFFF
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        // Rotate the channels (r,g,b) -> (b,r,g) and mix halfway: a neighbouring hue of the same weight.
        val turned = (((r + b) / 2) shl 16) or (((g + r) / 2) shl 8) or ((b + g) / 2)
        return intArrayOf(0, (a shl 24) or rgb, ((a * 3 / 4) shl 24) or turned, 0)
    }
}
