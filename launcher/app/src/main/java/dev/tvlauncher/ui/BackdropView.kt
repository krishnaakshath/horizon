package dev.tvlauncher.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import dev.tvlauncher.ambient.HomeWallpaperProvider

/** Pure compositing rule: over a wallpaper base layer the brand gradient is drawn at 55 %. */
object BackdropLayers {
    const val BRAND_TINT_ALPHA = HomeWallpaperProvider.BRAND_TINT_ALPHA
    fun glowAlpha(hasBaseLayer: Boolean): Int = if (hasBaseLayer) (255 * BRAND_TINT_ALPHA).toInt() else 255
}

/**
 * Full-screen brand-tinted backdrop. One diagonal gradient (accent at top-left fading to the
 * near-black base) whose colors crossfade on focus changes, plus a static bottom scrim that keeps
 * the dock area calm. Nothing is allocated per frame: the color arrays and animator are reused.
 * Optional [setBaseLayer] (Settings > Ambient > "Use wallpaper on home") draws a drawable under the
 * gradient, which then drops to [BackdropLayers.BRAND_TINT_ALPHA]. The caller owns its lifetime.
 */
class BackdropView(context: Context, durationMs: Long) : View(context) {
    private val evaluator = ArgbEvaluator()
    private val from = IntArray(STOPS)
    private val to = IntArray(STOPS)
    private val now = IntArray(STOPS) { Theme.BASE }
    private val glow = GradientDrawable(GradientDrawable.Orientation.TL_BR, now.copyOf())
    private val scrim = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt())
    )
    private var base: Drawable? = null
    private var accent = 0
    private var hasAccent = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { a ->
            val t = a.animatedFraction
            for (i in 0 until STOPS) now[i] = evaluator.evaluate(t, from[i], to[i]) as Int
            glow.colors = now
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
    }

    /** Color the hero text sits on (top-left of the gradient). */
    val heroColor: Int get() = to[0]

    fun setAccent(color: Int, animate: Boolean = true) {
        if (hasAccent && color == accent) return
        hasAccent = true
        accent = color
        val a = BrandColors.backdropAccent(color)
        System.arraycopy(now, 0, from, 0, STOPS)
        to[0] = BrandColors.blend(Theme.BASE, a, 0.62f)
        to[1] = BrandColors.blend(Theme.BASE, a, 0.26f)
        to[2] = Theme.BASE
        animator.cancel()
        if (animate && animator.duration > 0) {
            animator.start()
        } else {
            System.arraycopy(to, 0, now, 0, STOPS)
            glow.colors = now
            invalidate()
        }
    }

    /** A full-bleed layer under the brand gradient, or null for the plain gradient. */
    fun setBaseLayer(d: Drawable?) {
        if (d === base) return
        base?.callback = null
        base = d
        d?.callback = this
        d?.setBounds(0, 0, width, height)
        glow.alpha = BackdropLayers.glowAlpha(d != null)
        invalidate()
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === base || super.verifyDrawable(who)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        glow.setBounds(0, 0, w, h)
        scrim.setBounds(0, 0, w, h)
        base?.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        base?.draw(canvas)
        glow.draw(canvas)
        scrim.draw(canvas)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STOPS = 3
    }
}
