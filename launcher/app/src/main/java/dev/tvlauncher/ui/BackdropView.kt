package dev.tvlauncher.ui

import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
 *
 * Glass: once the colours settle (debounced, never per frame) the settled gradient is published to
 * [GlassBackdrop]; with [glassBlur] (FULL) a 1/8-scale copy is drawn once on the main thread and
 * box-blurred on the glass thread. The copy is rebuilt only on a size, room, accent or base-layer
 * change, and dropped on detach.
 *
 * Media layer (Now Playing, see dev.tvlauncher.nowplaying.MediaBackdrop): [setMedia] hands over a
 * small, already blurred and darkened picture of the playing item's art, drawn scaled to cover over
 * the brand gradient (under the bottom scrim) whenever [showMedia] is on. A new picture crossfades
 * over the old one in [mediaFadeMs]; showing or hiding the layer fades the same way. The view owns
 * those bitmaps: one that is no longer drawn is recycled right after the next frame that no longer
 * draws it (never while a display list may still hold it), or dropped for the GC when not visible.
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
    /** FULL: also publish a blurred copy for frosted glass. Off (LITE) costs nothing. */
    var glassBlur = false
    private var small: Bitmap? = null
    private var smallCanvas: Canvas? = null
    private var blurToken = 0
    private var baseDirty = false
    private val sampleRunnable = Runnable { sample() }
    // --- media layer ---
    private val mediaPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val mediaSrc = Rect()
    private val mediaDst = Rect()
    private var media: Bitmap? = null
    private var mediaOld: Bitmap? = null
    private val mediaStops = IntArray(STOPS)
    private var trackFade = 1f
    private var mediaShow = 0f
    private var mediaFrom = 0f
    private var mediaTarget = false
    private val retired = ArrayList<Bitmap>(2)
    /** Retired pictures that a recorded frame no longer draws: safe to recycle. */
    private val undrawn = ArrayList<Bitmap>(2)
    private val recycleRetired = Runnable { recycleRetiredNow() }
    /** Crossfade of the media layer (track change, show/hide); 0 = instant. */
    var mediaFadeMs: Long = 350L
    private val trackAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { a -> trackFade = a.animatedFraction; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { retireOld() }
        })
    }
    private val showAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { a ->
            val t = a.animatedFraction
            mediaShow = mediaFrom + ((if (mediaTarget) 1f else 0f) - mediaFrom) * t
            invalidate()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!mediaTarget && mediaShow <= 0f) dropMediaIfHidden()
            }
        })
    }
    /** setMedia(null) while shown: the picture fades out first, then goes. */
    private var dropWhenHidden = false
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

    /** Color the hero text sits on (top-left of the gradient, or of the media picture when shown). */
    val heroColor: Int get() = if (mediaTarget && media != null) mediaStops[0] else to[0]

    /** True while the media layer is (or is fading) on. */
    val isShowingMedia: Boolean get() = mediaTarget && media != null

    /**
     * The media picture ([bmp], owned by this view from now on; null = none) and its three
     * representative colours ([stops]: top-left, middle, bottom-right) for the hero and glass.
     * Main thread. A different picture crossfades over the current one when [animate].
     */
    fun setMedia(bmp: Bitmap?, stops: IntArray?, animate: Boolean = true) {
        if (bmp === media) return
        if (bmp == null) {
            if (mediaShow > 0f && mediaTarget && animate && mediaFadeMs > 0 && isShown) {
                dropWhenHidden = true
                showMedia(false, animate = true)
            } else {
                showAnim.cancel(); trackAnim.cancel()
                mediaTarget = false; mediaShow = 0f
                retire(mediaOld); mediaOld = null
                retire(media); media = null
                invalidate()
                scheduleSample()
            }
            return
        }
        dropWhenHidden = false
        if (stops != null && stops.size >= STOPS) System.arraycopy(stops, 0, mediaStops, 0, STOPS)
        trackAnim.cancel() // ends the previous fade: its old picture is retired
        val old = media
        media = bmp
        if (old != null && mediaShow > 0f && animate && mediaFadeMs > 0 && isShown) {
            mediaOld = old
            trackFade = 0f
            trackAnim.duration = mediaFadeMs
            trackAnim.start()
        } else {
            retire(old)
            trackFade = 1f
        }
        invalidate()
        scheduleSample()
    }

    /** Shows (art over the brand colours) or hides the media layer, fading in [mediaFadeMs]. */
    fun showMedia(show: Boolean, animate: Boolean = true) {
        if (show == mediaTarget && (showAnim.isRunning || mediaShow == (if (show) 1f else 0f))) return
        if (show) dropWhenHidden = false
        mediaTarget = show
        showAnim.cancel()
        mediaFrom = mediaShow
        if (animate && mediaFadeMs > 0 && isShown) {
            showAnim.duration = mediaFadeMs
            showAnim.start()
        } else {
            mediaShow = if (show) 1f else 0f
            if (!show) dropMediaIfHidden()
            invalidate()
        }
        scheduleSample()
    }

    private fun dropMediaIfHidden() {
        if (!dropWhenHidden || mediaTarget) return
        dropWhenHidden = false
        trackAnim.cancel()
        retire(mediaOld); mediaOld = null
        retire(media); media = null
        invalidate()
    }

    private fun retireOld() {
        val o = mediaOld ?: return
        mediaOld = null
        retire(o)
        invalidate()
    }

    /** No longer drawn: recycled after the next frame (or left to the GC when nothing will draw). */
    private fun retire(b: Bitmap?) {
        if (b == null) return
        retired.add(b)
        if (!isAttachedToWindow || !isShown) { retired.clear(); return } // GC; a stale display list may hold it
        invalidate()
    }

    private fun recycleRetiredNow() {
        for (b in undrawn) if (b !== media && b !== mediaOld && !b.isRecycled) b.recycle()
        undrawn.clear()
    }

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
        scheduleSample()
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
        scheduleSample()
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
        baseDirty = true
        scheduleSample()
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === base || super.verifyDrawable(who)

    /** The base layer finished loading (or changed): the glass sample follows. */
    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
        if (drawable === base) { baseDirty = true; scheduleSample() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        glow.setBounds(0, 0, w, h)
        scrim.setBounds(0, 0, w, h)
        base?.setBounds(0, 0, w, h)
        scheduleSample()
    }

    /** Back in front (e.g. Home after Settings): this window's backdrop is the glass sample again. */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) scheduleSample(now = true)
    }

    /** Publishes after the colours settle: [SAMPLE_SETTLE_MS] after the last change or crossfade. */
    private fun scheduleSample(now: Boolean = false) {
        removeCallbacks(sampleRunnable)
        val wait = if (now) 0L else SAMPLE_SETTLE_MS + if (animator.isRunning) animator.duration else 0L
        postDelayed(sampleRunnable, wait)
    }

    private fun sample() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || !isAttachedToWindow) return
        if (animator.isRunning || showAnim.isRunning || trackAnim.isRunning) return scheduleSample()
        val stops = if (mediaTarget && media != null) mediaStops else to
        val changed = GlassBackdrop.publish(this, stops, scrimAlpha, w, h)
        if (glassBlur && (changed || baseDirty || GlassBackdrop.blur == null)) blurCopy(w, h)
        baseDirty = false
    }

    /** One small copy drawn here (main thread, microseconds at 1/8 scale), blurred off the main thread. */
    private fun blurCopy(w: Int, h: Int) {
        val (sw, sh) = BoxBlur.scaled(w, h)
        var bmp = small
        var c = smallCanvas
        if (bmp == null || c == null || bmp.width != sw || bmp.height != sh) {
            bmp?.recycle() // only ever a software render target, never in a display list
            bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            c = Canvas(bmp)
            small = bmp; smallCanvas = c
        }
        val px = IntArray(sw * sh)
        try {
            c.drawColor(Theme.BASE)
            c.save()
            c.scale(sw / w.toFloat(), sh / h.toFloat())
            base?.draw(c)
            glow.draw(c)
            drawMedia(c)
            scrim.draw(c)
            c.restore()
            bmp.getPixels(px, 0, sw, 0, 0, sw, sh)
        } catch (e: RuntimeException) {
            return // e.g. a hardware bitmap in the base layer: the gradient fallback stays
        }
        val token = ++blurToken
        try {
            GlassBackdrop.executor.execute {
                BoxBlur.blur(px, sw, sh, BoxBlur.RADIUS)
                val brightest = GlassTokens.brightest(px)
                val out = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
                GlassBackdrop.main.post {
                    if (token == blurToken && isAttachedToWindow) GlassBackdrop.setBlur(this, out, brightest)
                }
            }
        } catch (e: RuntimeException) {
        }
    }

    override fun onDraw(canvas: Canvas) {
        base?.draw(canvas)
        glow.draw(canvas)
        drawMedia(canvas)
        scrim.draw(canvas)
        // This frame no longer draws the retired pictures; once it is synced they can go.
        if (retired.isNotEmpty()) {
            undrawn.addAll(retired)
            retired.clear()
            removeCallbacks(recycleRetired)
            post(recycleRetired)
        }
    }

    private fun drawMedia(canvas: Canvas) {
        if (mediaShow <= 0f) return
        val cur = media ?: return
        val a = (255 * mediaShow).toInt().coerceIn(0, 255)
        val old = mediaOld
        if (old != null && trackFade < 1f) {
            drawCover(canvas, old, a)
            drawCover(canvas, cur, (a * trackFade).toInt())
        } else {
            drawCover(canvas, cur, a)
        }
    }

    /** The picture already has the screen's aspect ratio (MediaBackdrop renders it so): stretch to fill. */
    private fun drawCover(canvas: Canvas, b: Bitmap, alpha: Int) {
        if (b.isRecycled || alpha <= 0) return
        mediaSrc.set(0, 0, b.width, b.height)
        mediaDst.set(0, 0, width, height)
        mediaPaint.alpha = alpha
        canvas.drawBitmap(b, mediaSrc, mediaDst, mediaPaint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        showAnim.cancel()
        trackAnim.cancel()
        removeCallbacks(recycleRetired)
        retired.clear() // left to the GC: a display list may still hold them
        undrawn.clear()
        media = null
        mediaOld = null
        mediaShow = 0f
        mediaTarget = false
        removeCallbacks(sampleRunnable)
        blurToken++
        GlassBackdrop.release(this)
        small?.recycle()
        small = null
        smallCanvas = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STOPS = 3
        const val SAMPLE_SETTLE_MS = 250L
    }
}
