package dev.tvlauncher.browser.ui

import android.content.Context
import android.graphics.drawable.Animatable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvlauncher.browser.Perf
import dev.tvlauncher.browser.R

/** Cold-start mark reveal: orbit draws itself, satellite pops, wordmark rises (<= 600 ms; LITE 350 ms). */
class SplashOverlay(context: Context) : FrameLayout(context) {
    init {
        setBackgroundColor(Theme.BASE)
        isClickable = true
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val lite = Perf.lite || Perf.animationsOff
        val mark = ImageView(context).apply { setImageResource(if (lite) R.drawable.brand_mark else R.drawable.brand_mark_reveal) }
        val word = ImageView(context).apply { setImageResource(R.drawable.brand_wordmark); alpha = 0f; translationY = context.dpf(10f) }
        col.addView(mark, LinearLayout.LayoutParams(context.dp(84), context.dp(84)))
        col.addView(word, LinearLayout.LayoutParams(context.dp(130), context.dp(24)).apply { topMargin = context.dp(18) })
        addView(col, LayoutParams(-2, -2, Gravity.CENTER))
        val total = if (lite) 350L else 600L
        if (lite) {
            mark.scaleX = 0.85f; mark.scaleY = 0.85f; mark.alpha = 0f
            mark.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
        } else post { (mark.drawable as? Animatable)?.start() }
        word.animate().alpha(1f).translationY(0f).setStartDelay(if (lite) 80 else 220).setDuration(if (lite) 200 else 300).start()
        postDelayed({
            animate().alpha(0f).setDuration(180).withEndAction { (parent as? android.view.ViewGroup)?.removeView(this) }.start()
        }, total)
    }
}
