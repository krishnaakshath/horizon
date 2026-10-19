package dev.tvlauncher.ui

import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
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
 * Pure colour rule for the backdrop under a room palette (dev.tvlauncher.room.RoomTheme). The
 * focused app's accent is mixed into the palette's top/bottom colours at a strength scaled by
 * [Palette.accentBlend] (STANDARD = today's 0.62 / 0.26 mix), every stop is lifted to at least the
 * palette's floor luminance (a bright room never shows pure black), and the bottom scrim follows
 * [Palette.scrimStrength].
 */
object RoomBackdrop {
    /** accentBlend of the STANDARD palette: the strength the base mix (0.62 / 0.26) was tuned at. */
    const val REFERENCE_BLEND = 0.55f
    const val TOP_MIX = 0.62f
    const val MID_MIX = 0.26f
    /** Bottom-scrim alpha at the reference strength (0x8C). */
    const val SCRIM_ALPHA = 0x8C

    /** Fills [out] (3 stops: top-left, middle, bottom-right). */
    fun stops(accent: Int, top: Int, bottom: Int, floor: Int, accentBlend: Float, out: IntArray) {
        val k = (accentBlend / REFERENCE_BLEND).coerceIn(0f, 1.5f)
        out[0] = lift(BrandColors.blend(top, accent, (TOP_MIX * k).coerceIn(0f, 1f)), floor)
        out[1] = lift(BrandColors.blend(bottom, accent, (MID_MIX * k).coerceIn(0f, 1f)), floor)
        out[2] = lift(bottom, floor)
    }

    /** [c], or [floor] when [c] is darker than it. */
    fun lift(c: Int, floor: Int): Int = if (BrandColors.luminance(c) < BrandColors.luminance(floor)) floor else c

    fun scrimAlpha(strength: Float): Int =
        Math.round(SCRIM_ALPHA * (strength / 0.55f)).coerceIn(0, 255)
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
    // Room palette (RoomTheme frame); defaults = the original near-black look.
    private var roomTop = Theme.BASE
    private var roomBottom = Theme.BASE
    private var roomFloor = Theme.BASE
    private var roomBlend = RoomBackdrop.REFERENCE_BLEND
    private var scrimAlpha = RoomBackdrop.SCRIM_ALPHA
    private val scrimColors = intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt())
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
        auditTag(AuditTags.FULLBLEED)
    }

    /** Color the hero text sits on (top-left of the gradient). */
    val heroColor: Int get() = to[0]

    fun setAccent(color: Int, animate: Boolean = true) {
        if (hasAccent && color == accent) return
        hasAccent = true
        accent = color
        System.arraycopy(now, 0, from, 0, STOPS)
        computeTargets()
        animator.cancel()
        if (animate && animator.duration > 0) {
            animator.start()
        } else {
            System.arraycopy(to, 0, now, 0, STOPS)
            glow.colors = now
            invalidate()
        }
    }

    private fun computeTargets() {
        val a = if (hasAccent) BrandColors.backdropAccent(accent) else roomBottom
        RoomBackdrop.stops(a, roomTop, roomBottom, roomFloor, roomBlend, to)
    }

    /**
     * Room lighting (RoomTheme frame, main thread; called for every crossfade frame). Retargets the
     * gradient without restarting the accent fade, and rescales the bottom scrim. No allocation.
     */
    fun setRoom(top: Int, bottom: Int, floor: Int, accentBlend: Float, scrimStrength: Float) {
        if (top == roomTop && bottom == roomBottom && floor == roomFloor && accentBlend == roomBlend &&
            RoomBackdrop.scrimAlpha(scrimStrength) == scrimAlpha) return
        roomTop = top; roomBottom = bottom; roomFloor = floor; roomBlend = accentBlend
        scrimAlpha = RoomBackdrop.scrimAlpha(scrimStrength)
        scrimColors[2] = scrimAlpha shl 24
        scrim.colors = scrimColors
        computeTargets()
        if (!animator.isRunning) {
            System.arraycopy(to, 0, now, 0, STOPS)
            glow.colors = now
        }
        invalidate()
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
