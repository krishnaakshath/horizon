package dev.tvlauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditAddTag
import dev.tvlauncher.room.RoomTheme
import dev.tvlauncher.settings.GlassStyle
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The launcher's one glass style (tokens and rules: [GlassTokens]).
 *
 * - [frost]: a floating card over content (Settings sidebar and pane, Spotlight, the panel shell).
 *   Opaque: it samples the backdrop behind it (FULL: the pre-blurred copy; LITE: the backdrop's
 *   gradient), then tint, sheen and edge. Nothing under it reads through, so text contrast is the
 *   tint's guarantee. FULL adds a soft shadow.
 * - [clear]: translucent glass with the same tint/sheen/edge but no backdrop sample (pills, the
 *   quick strip): it sits directly on the backdrop.
 * - [item]: the faint glass of a resting row or entry on a pane.
 *
 * Nothing allocates per frame: shaders are rebuilt only when the bounds or the backdrop sample
 * ([GlassBackdrop.version]) change.
 */
object Glass {
    /** Frosted card on [view] (sets its background; FULL: elevation shadow). Returns the drawable. */
    fun frost(view: View, full: Boolean, radiusDp: Int = GlassTokens.RADIUS_PANEL, overContentScrim: Float = -1f): GlassDrawable {
        val d = GlassDrawable(view.context, GlassDrawable.Kind.FROSTED, radiusDp, full)
        d.host = view
        d.overContentScrim = overContentScrim
        view.background = d
        if (full) {
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.elevation = view.context.dp(GlassTokens.SHADOW_DP).toFloat()
        }
        GlassBackdrop.track(view)
        // A glass card: the auditor reads its coverage (AuditOpacity) and the scrims behind it, and
        // treats it as a card, not content, even when clickable (focusable on API 26+).
        view.auditAddTag(AuditTags.GLASS)
        return d
    }

    /**
     * Liquid Glass: the card arrives with a little spring (scale 0.94 -> 1 with overshoot, the
     * corners morphing from a capsule). Other styles, or motion off: nothing. One short-lived
     * animator per open, never per frame.
     */
    fun springIn(view: View, d: GlassDrawable?, full: Boolean) {
        val look = GlassLooks.of(GlassBackdrop.style, full)
        if (!look.spring || !Motion.enabled) { d?.morph = 1f; return }
        val ms = Motion.ms(if (full) SPRING_MS else SPRING_LITE_MS)
        if (ms <= 0) return
        view.scaleX = SPRING_FROM; view.scaleY = SPRING_FROM
        view.animate().scaleX(1f).scaleY(1f).setDuration(ms).setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
        if (d != null) {
            d.morph = 0f
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ms
                interpolator = android.view.animation.DecelerateInterpolator(1.6f)
                addUpdateListener { d.morph = it.animatedValue as Float }
                start()
            }
        }
    }

    /** Focus scale interpolator: springy for Liquid Glass, calm otherwise. */
    fun focusInterpolator(full: Boolean): android.animation.TimeInterpolator =
        if (GlassLooks.of(GlassBackdrop.style, full).spring && Motion.enabled) SPRINGY else CALM

    private val SPRINGY = android.view.animation.OvershootInterpolator(1.6f)
    private val CALM = android.view.animation.DecelerateInterpolator()

    /**
     * Focus scale of [view] to [scale] over [ms] with [focusInterpolator]: springy for Liquid
     * Glass, calm otherwise; instant when [ms] is 0 or motion is off (reduced motion).
     */
    fun focusScale(view: View, scale: Float, ms: Long, full: Boolean) {
        view.animate().cancel()
        if (ms <= 0L || !Motion.enabled) { view.scaleX = scale; view.scaleY = scale; return }
        view.animate().scaleX(scale).scaleY(scale).setDuration(ms).setInterpolator(focusInterpolator(full)).start()
    }

    const val SPRING_MS = 300L
    const val SPRING_LITE_MS = 240L
    const val SPRING_FROM = 0.94f

    /** Translucent glass with the adaptive tint. Call [track] on the view that shows it. */
    fun clear(context: Context, full: Boolean, radiusDp: Int): GlassDrawable =
        GlassDrawable(context, GlassDrawable.Kind.CLEAR, radiusDp, full)

    /** A resting row or entry on a pane: [fill] plus sheen and a fainter edge. */
    fun item(context: Context, radiusDp: Int = GlassTokens.RADIUS_ITEM, fill: Int = GlassTokens.ROW_REST): GlassDrawable =
        GlassDrawable(context, GlassDrawable.Kind.ITEM, radiusDp, false).also { it.fixedFill = fill }

    /** Plain rounded fill (focus states, chips). */
    fun rect(context: Context, fill: Int, radiusDp: Int, stroke: Int = 0, strokeDp: Int = 1) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = context.dp(GlassBackdrop.corners.radiusDp(radiusDp)).toFloat()
            if (stroke != 0) setStroke(context.dp(strokeDp), stroke)
        }

    /** Repaints [view] whenever the backdrop sample changes (adaptive tint, blur). */
    fun track(view: View) = GlassBackdrop.track(view)
}

/**
 * What glass samples: the backdrop of the window in front (main thread only). The visible
 * activity's [BackdropView] publishes its SETTLED gradient here (debounced, never per frame)
 * and, in FULL, a blurred 1/8-scale copy made once on [executor]. Views registered with [track]
 * are invalidated on every publish. Memory: one small bitmap (57 KB at 720p, 0.5 MB at 4K).
 */
object GlassBackdrop {
    /** Settled gradient stops (top-left, middle, bottom-right) in window coordinates. */
    val stops = intArrayOf(Theme.BASE, Theme.BASE, Theme.BASE)
    var scrimAlpha = RoomBackdrop.SCRIM_ALPHA
        private set
    var width = 0
        private set
    var height = 0
        private set
    /** FULL: the blurred copy (1/[BoxBlur.SCALE] of the backdrop); null in LITE or until ready. */
    var blur: Bitmap? = null
        private set
    var version = 0
        private set
    /** Tint for this backdrop and room ([GlassTokens.tint]). */
    var tint: Int = GlassTokens.tint(null, Theme.BASE)
        private set
    /** Lighter tint of CLEAR glass ([GlassTokens.clearTint]). */
    var clearTint: Int = GlassTokens.clearTint(null, Theme.BASE)
        private set
    /** The brightest colour of the current backdrop (blur pixels when known; white with an unknown layer). */
    var worst: Int = Theme.BASE
        private set
    /** True while the backdrop has a layer whose pixels LITE cannot know (wallpaper, media art). */
    var unknownLayer = false
        set(v) { if (field != v) { field = v; if (blur == null) retint(null) } }
    /** Settings > Appearance > Glass style, resolved for this TV (set by Home and Settings). */
    var style: GlassStyle = GlassStyle.FROSTED
        private set

    /** True on a FULL-tier TV (set with the style by Home and Settings): springy focus only there. */
    @Volatile var fullTier = false

    /** Applies a glass style everywhere: every tracked glass view repaints in it. */
    fun setStyle(s: GlassStyle) {
        if (s == style) return
        style = s
        clearLens()
        version++
        for (i in hosts.indices.reversed()) hosts.getOrNull(i)?.invalidate()
    }

    /** Settings > Appearance > Look > Glass amount, 0..100 ([GlassAmount]). */
    var amount: Int = GlassAmount.NEUTRAL
        private set
    /** Settings > Appearance > Look > Corners: scales every glass radius. */
    var corners: dev.tvlauncher.look.Corners = dev.tvlauncher.look.Corners.ROUND
        private set
    /** Settings > Appearance > Look > Tint. */
    var tintSource: dev.tvlauncher.look.LookTint = dev.tvlauncher.look.LookTint.BACKDROP
        private set
    /** The accent used by [dev.tvlauncher.look.LookTint.ACCENT] glass. */
    var accent: Int = 0xFF4C8DFF.toInt()
        private set
    /** FULL: scale denominator of the next blurred backdrop copy ([GlassAmount.blurDenominator]). */
    @Volatile var blurScale: Int = BoxBlur.SCALE
        private set

    /** Applies the look's glass values live: every tracked glass view repaints (no restart). */
    fun setLook(amount: Int, corners: dev.tvlauncher.look.Corners, tint: dev.tvlauncher.look.LookTint, accent: Int) {
        val a = amount.coerceIn(0, 100)
        if (a == this.amount && corners == this.corners && tint == tintSource && accent == this.accent) return
        val lensOff = a != this.amount
        this.amount = a; this.corners = corners; this.tintSource = tint; this.accent = accent
        blurScale = GlassAmount.blurDenominator(a, true)
        if (lensOff) clearLens()
        retint(lastBlurBrightest)
    }

    private var lastBlurBrightest: Int? = null

    private var owner: Any? = null
    private val hosts = ArrayList<View>()
    private val pair = IntArray(2)
    private val tracker = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { if (v !in hosts) hosts.add(v) }
        override fun onViewDetachedFromWindow(v: View) { hosts.remove(v) }
    }

    val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "glass-blur").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    }
    val main: Handler by lazy { Handler(Looper.getMainLooper()) }

    fun track(view: View) {
        view.removeOnAttachStateChangeListener(tracker)
        view.addOnAttachStateChangeListener(tracker)
        if (view.isAttachedToWindow && view !in hosts) hosts.add(view)
    }

    /** A new settled backdrop from [from]; false when nothing changed. A changed sample drops the old blur. */
    fun publish(from: Any, s: IntArray, scrim: Int, w: Int, h: Int): Boolean {
        val same = owner === from && w == width && h == height && scrim == scrimAlpha &&
            s[0] == stops[0] && s[1] == stops[1] && s[2] == stops[2]
        if (same) return false
        owner = from
        System.arraycopy(s, 0, stops, 0, 3)
        scrimAlpha = scrim; width = w; height = h
        blur = null
        clearLens()
        retint(null)
        return true
    }

    /** The blurred copy for the current sample of [from]; [brightest] is its brightest pixel. */
    fun setBlur(from: Any, bitmap: Bitmap, brightest: Int) {
        if (owner !== from) return
        blur = bitmap
        lastBlurBrightest = brightest
        clearLens()
        retint(brightest)
    }

    /** [from] went away: forget its sample. The bitmap is left to the GC (never recycle() a bitmap a RenderNode may still hold). */
    fun release(from: Any) {
        if (owner !== from) return
        owner = null
        blur = null
        clearLens()
        version++
    }

    // ---- Liquid Glass lens (FULL): per panel crop of [blur], remapped on [executor] ----

    /** Bumped whenever a lens bitmap arrives or the cache is dropped: drawables re-query then. */
    var lensGen = 0
        private set
    /** Lens bitmaps by crop ([LensPlan.key]); dropped (left to the GC, never recycle()d while a RenderNode may hold them) on style/backdrop change. */
    private val lensCache = LensLru<Bitmap>(LensPlan.MAX_ENTRIES)
    private val pending = LongArray(LensPlan.MAX_ENTRIES)
    private var pendingCount = 0
    private var lensEpoch = 0
    /** Executor thread only: the last displacement table (reused while the crop size holds). */
    private var table: LensMath.Table? = null

    /**
     * The lens bitmap for crop [c] ([LensPlan.crop]) of the current blur, or null while it is being
     * built (the caller draws the plain sample meanwhile: no flicker, the lens fades in on arrival).
     * Called only when a drawable's crop or [lensGen] changed, never per frame.
     */
    fun lens(c: IntArray, chroma: Boolean): Bitmap? {
        val src = blur ?: return null
        val key = LensPlan.key(c)
        lensCache.get(key)?.let { return it }
        for (i in 0 until pendingCount) if (pending[i] == key) return null
        if (pendingCount >= pending.size) return null
        pending[pendingCount++] = key
        val crop = c.copyOf()
        val epoch = lensEpoch
        executor.execute {
            val bmp = try { buildLens(src, crop, chroma) } catch (_: Throwable) { null }
            main.post {
                removePending(key)
                if (bmp != null && epoch == lensEpoch && blur === src) {
                    lensCache.put(key, bmp)
                    lensGen++
                    for (i in hosts.indices.reversed()) hosts.getOrNull(i)?.invalidate()
                }
            }
        }
        return null
    }

    private fun removePending(key: Long) {
        for (i in 0 until pendingCount) if (pending[i] == key) {
            pending[i] = pending[pendingCount - 1]; pendingCount--; return
        }
    }

    /** Executor thread: crop the blur, remap it through the (cached) displacement table. */
    private fun buildLens(src: Bitmap, c: IntArray, chroma: Boolean): Bitmap? {
        val w = c[LensPlan.W]; val h = c[LensPlan.H]
        if (src.isRecycled || c[LensPlan.X] + w > src.width || c[LensPlan.Y] + h > src.height) return null
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, c[LensPlan.X], c[LensPlan.Y], w, h)
        val bx = c[LensPlan.BAND_X]; val by = c[LensPlan.BAND_Y]
        val t = table?.takeIf { it.matches(w, h, bx, by, chroma) } ?: LensMath.Table(w, h, bx, by, chroma).also { table = it }
        val out = IntArray(w * h)
        LensMath.remap(px, t, out)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun clearLens() {
        lensEpoch++
        lensCache.clear()
        pendingCount = 0
        lensGen++
    }

    private fun retint(blurBrightest: Int?) {
        var worst = GlassTokens.brightest(stops)
        if (blurBrightest != null) {
            pair[0] = worst; pair[1] = blurBrightest
            worst = GlassTokens.brightest(pair)
        }
        if (blurBrightest == null && unknownLayer) worst = 0xFFFFFFFF.toInt()
        this.worst = worst
        if (blurBrightest == null) lastBlurBrightest = null
        tint = GlassTokens.tint(RoomTheme.palette, worst)
        clearTint = GlassTokens.clearTint(RoomTheme.palette, worst)
        version++
        for (i in hosts.indices.reversed()) hosts.getOrNull(i)?.invalidate()
    }
}

/**
 * The glass drawable (see [Glass]), drawn in the look of the current [GlassBackdrop.style] on this
 * device tier ([GlassLooks]): backdrop sample (blur or gradient; saturation/lift via one cached
 * colour filter), tint, text scrim over the content area (inside the rim band), sheen, inner glow,
 * specular rim (lit top-left, dim opposite) and a faint outer edge. [Kind.FROSTED] uses [host] to
 * align its sample with the window. Shaders and the resolved colours are rebuilt only when the
 * bounds, the backdrop sample or the style change: nothing is allocated per frame.
 */
class GlassDrawable(context: Context, val kind: Kind, radiusDp: Int, private val full: Boolean) :
    Drawable(), dev.tvlauncher.audit.AuditOpacity {
    enum class Kind { FROSTED, CLEAR, ITEM }

    var host: View? = null
    /** ITEM fill (ARGB); ignored by the other kinds. */
    var fixedFill: Int = GlassTokens.ROW_REST
    /**
     * Alpha of the scrim between this card and the content behind it (panels over the home shelf),
     * or -1 when only the backdrop is behind (pills, Settings panes). Used for the contrast guarantee.
     */
    var overContentScrim: Float = -1f
        set(v) { field = v; resolvedKey = -1 }
    /** 0..1 shape morph (Liquid Glass spring): radius grows toward a capsule while < 1. */
    var morph: Float = 1f
        set(v) { field = v; invalidateSelf() }

    private val density = context.resources.displayMetrics.density
    private val baseRadiusDp = radiusDp
    private var radius = Math.round(GlassBackdrop.corners.radiusDp(radiusDp) * density).toFloat()
    private val edgePx = GlassTokens.edgePx(density).toFloat()
    private val rect = RectF()
    private val edgeRect = RectF()
    private val inner = RectF()
    private val base = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textScrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = edgePx }
    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = edgePx }
    private val chroma = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = edgePx }
    private val matrix = Matrix()
    private val loc = IntArray(2)
    private var alphaMul = 255
    private var sampleVersion = -1
    private var gradient: Shader? = null
    private var scrimShader: Shader? = null
    private var bitmapShader: BitmapShader? = null
    private var shaderBitmap: Bitmap? = null
    private var look: GlassLook = GlassLooks.of(GlassBackdrop.style, full)
    private var resolved: GlassLooks.Resolved = GlassLooks.resolve(look, null, Theme.BASE, null)
    private var resolvedKey = -1
    private var shaderLook: GlassLook? = null
    private var boostFilter: ColorFilter? = null
    private val crop = IntArray(6)
    private var lensKey = Long.MIN_VALUE
    private var lensGenSeen = -1
    private var lensSrc: Bitmap? = null
    private var lensBitmap: Bitmap? = null
    private var lensShader: BitmapShader? = null

    /** Rebuilds look, colours and the bounds-dependent shaders when the sample or style changed. */
    private fun ensure() {
        val g = GlassBackdrop
        val key = g.version * 8 + g.style.ordinal
        if (key == resolvedKey) return
        resolvedKey = key
        radius = Math.round(g.corners.radiusDp(baseRadiusDp) * density).toFloat()
        look = GlassLooks.tuned(g.style, full, g.amount).let { l ->
            // Pills and the quick strip sit on the backdrop itself: never a sample of their own.
            if (kind == Kind.FROSTED) l else l.copy(sample = GlassLook.Sample.NONE, sampleAlpha = 0f, lens = false,
                tint = minOf(l.tint, if (l.style == dev.tvlauncher.settings.GlassStyle.SOLID) l.tint else 0.55f))
        }
        val content = if (overContentScrim >= 0f) GlassLooks.dimmedWhite(overContentScrim) else null
        resolved = GlassLooks.resolve(look, RoomTheme.palette, g.worst, content, GlassLooks.tintOverride(g.tintSource, g.accent))
        boostFilter = if (look.saturation != 1f || look.lift != 0f) colorBoostFilter(look.saturation, look.lift) else null
        if (shaderLook != look) { shaderLook = look; buildBoundShaders(bounds) }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildBoundShaders(bounds)
    }

    private fun buildBoundShaders(b: Rect) {
        val h = b.height().toFloat().coerceAtLeast(1f)
        val w = b.width().toFloat().coerceAtLeast(1f)
        val top = b.top.toFloat()
        val left = b.left.toFloat()
        val l = look
        // Sheen from the lit top-left corner.
        sheen.shader = LinearGradient(left, top, left + w * 0.6f, top + h * l.sheenFraction,
            l.sheenTop, 0, Shader.TileMode.CLAMP)
        val lit = if (kind == Kind.ITEM) GlassTokens.EDGE_TOP_ITEM else l.rimLit
        val shade = if (kind == Kind.ITEM) GlassTokens.EDGE_BOTTOM else l.rimShade
        edge.shader = LinearGradient(left, top, left + w * 0.35f, top + h, lit, shade, Shader.TileMode.CLAMP)
        glow.strokeWidth = density * l.glowDp
        glow.shader = if (l.innerGlow != 0) LinearGradient(left, top, left, top + h, l.innerGlow,
            l.innerGlow and 0x00FFFFFF or ((l.innerGlow ushr 25) shl 24), Shader.TileMode.CLAMP) else null
        chroma.shader = if (l.chroma) LinearGradient(left, top, left + w, top + h,
            intArrayOf(0x14FF4060, 0, 0x144060FF), null, Shader.TileMode.CLAMP) else null
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        ensure()
        rect.set(bounds)
        val l = look
        val r = currentRadius()
        if (kind == Kind.FROSTED && l.sample != GlassLook.Sample.NONE) drawSample(canvas, r, l)
        val tint = if (kind == Kind.ITEM) fixedFill else resolved.tint
        fill.color = tint
        fill.alpha = (((tint ushr 24) and 0xFF) * alphaMul) / 255
        canvas.drawRoundRect(rect, r, r, fill)
        if (kind != Kind.ITEM && resolved.scrimAlpha > 0f) {
            // Behind text: the content area, inside the rim band, so the rim stays glassy.
            val band = GlassLooks.bandPx(bounds.width(), bounds.height(), density).toFloat()
            textScrim.color = resolved.scrim
            val total = (((resolved.scrim ushr 24) and 0xFF) * alphaMul) / 255
            // A feathered edge instead of a hard rectangle: SCRIM_STEPS layers inset from a quarter
            // band to the full band, each with the alpha that makes the stack compose to exactly
            // [total] inside the band (where the text is), so contrast is unchanged.
            textScrim.alpha = GlassScrim.stepAlpha(total, SCRIM_STEPS)
            for (i in 0 until SCRIM_STEPS) {
                val inset = band * GlassScrim.insetFraction(i, SCRIM_STEPS)
                inner.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
                if (inner.width() <= 0f || inner.height() <= 0f) inner.set(rect)
                val ir = (r - inset).coerceAtLeast(r / 2f)
                canvas.drawRoundRect(inner, ir, ir, textScrim)
            }
        }
        sheen.alpha = alphaMul
        canvas.drawRoundRect(rect, r, r, sheen)
        if (glow.shader != null && kind != Kind.ITEM) {
            val gh = glow.strokeWidth / 2f
            inner.set(rect.left + gh, rect.top + gh, rect.right - gh, rect.bottom - gh)
            glow.alpha = alphaMul
            canvas.drawRoundRect(inner, (r - gh).coerceAtLeast(0f), (r - gh).coerceAtLeast(0f), glow)
        }
        val half = edgePx / 2f
        if (l.outerEdge != 0 && kind != Kind.ITEM) {
            edgeRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
            outer.color = l.outerEdge
            outer.alpha = (((l.outerEdge ushr 24) and 0xFF) * alphaMul) / 255
            canvas.drawRoundRect(edgeRect, (r - half).coerceAtLeast(0f), (r - half).coerceAtLeast(0f), outer)
        }
        // The specular rim, one pixel inside the outer edge.
        val e = if (l.outerEdge != 0 && kind != Kind.ITEM) edgePx + half else half
        edgeRect.set(rect.left + e, rect.top + e, rect.right - e, rect.bottom - e)
        edge.alpha = alphaMul
        canvas.drawRoundRect(edgeRect, (r - e).coerceAtLeast(0f), (r - e).coerceAtLeast(0f), edge)
        if (chroma.shader != null && kind == Kind.FROSTED) {
            chroma.alpha = alphaMul
            canvas.drawRoundRect(edgeRect, (r - e).coerceAtLeast(0f), (r - e).coerceAtLeast(0f), chroma)
        }
    }

    /** The radius, morphing toward a capsule while [morph] < 1 (Liquid Glass spring). */
    private fun currentRadius(): Float {
        if (morph >= 1f) return radius
        val cap = minOf(rect.width(), rect.height()) / 2f
        return radius + (cap - radius).coerceAtLeast(0f) * (1f - morph.coerceIn(0f, 1f))
    }

    /** The backdrop behind the card: shaders live in window coordinates, shifted by the host's position. */
    private fun drawSample(canvas: Canvas, r: Float, l: GlassLook) {
        val g = GlassBackdrop
        if (sampleVersion != g.version) rebuildSample()
        layoutLocation(host, loc)
        val dx = -loc[0].toFloat()
        val dy = -loc[1].toFloat()
        val bs = bitmapShader
        val bmp = shaderBitmap
        val a = (l.sampleAlpha * alphaMul).toInt().coerceIn(0, 255)
        base.alpha = a
        base.colorFilter = boostFilter
        if (l.sample == GlassLook.Sample.BLUR && bs != null && bmp != null && g.width > 0) {
            if (l.lens && full && drawLens(canvas, r, g, bmp, dx, dy)) return
            matrix.setScale(g.width / bmp.width.toFloat(), g.height / bmp.height.toFloat())
            matrix.postTranslate(dx, dy)
            bs.setLocalMatrix(matrix)
            base.shader = bs
            canvas.drawRoundRect(rect, r, r, base)
            return
        }
        val gs = gradient ?: return
        matrix.setTranslate(dx, dy)
        gs.setLocalMatrix(matrix)
        base.shader = gs
        canvas.drawRoundRect(rect, r, r, base)
        val ss = scrimShader ?: return
        ss.setLocalMatrix(matrix)
        scrim.shader = ss
        scrim.alpha = a
        canvas.drawRoundRect(rect, r, r, scrim)
    }

    /**
     * Liquid Glass: the lens-remapped crop of the blur under this card, when built (else false and
     * the plain sample is drawn). The crop is re-resolved only when the card's crop or the lens
     * cache changed; drawing reuses the shader, matrix and boost filter (no per-frame allocation).
     */
    private fun drawLens(canvas: Canvas, r: Float, g: GlassBackdrop, bmp: Bitmap, dx: Float, dy: Float): Boolean {
        val band = GlassLooks.bandPx(bounds.width(), bounds.height(), density)
        if (!LensPlan.crop(-dx.toInt() + bounds.left, -dy.toInt() + bounds.top, bounds.width(), bounds.height(), band,
                bmp.width, bmp.height, g.width, g.height, crop)) return false
        val key = LensPlan.key(crop)
        if (key != lensKey || lensGenSeen != g.lensGen || lensSrc !== bmp) {
            lensKey = key; lensGenSeen = g.lensGen; lensSrc = bmp
            val lb = g.lens(crop, look.chroma)
            if (lb !== lensBitmap) {
                lensBitmap = lb
                lensShader = lb?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            }
        }
        val ls = lensShader ?: return false
        matrix.setTranslate(crop[LensPlan.X].toFloat(), crop[LensPlan.Y].toFloat())
        matrix.postScale(g.width / bmp.width.toFloat(), g.height / bmp.height.toFloat())
        matrix.postTranslate(dx, dy)
        ls.setLocalMatrix(matrix)
        base.shader = ls
        canvas.drawRoundRect(rect, r, r, base)
        return true
    }

    /**
     * [v]'s resting position in its window from layout alone (translations ignored): an entry or
     * slide animation moves the card over a still backdrop sample, and the recorded drawing is
     * right again once the animation ends without a re-record. No allocation.
     */
    private fun layoutLocation(v: View?, out: IntArray) {
        out[0] = 0; out[1] = 0
        if (v == null) return
        var x = v.left
        var y = v.top
        var p = v.parent
        while (p is View) {
            x += p.left - p.scrollX
            y += p.top - p.scrollY
            p = p.parent
        }
        out[0] = x; out[1] = y
    }

    /** Runs only when the published sample changed (never per frame). */
    private fun rebuildSample() {
        val g = GlassBackdrop
        sampleVersion = g.version
        val w = if (g.width > 0) g.width.toFloat() else 1920f
        val h = if (g.height > 0) g.height.toFloat() else 1080f
        // Same geometry as BackdropView: a TL_BR three-stop gradient, then a TOP_BOTTOM scrim (0, 0, scrim).
        gradient = LinearGradient(0f, 0f, w, h, intArrayOf(g.stops[0], g.stops[1], g.stops[2]), null, Shader.TileMode.CLAMP)
        scrimShader = if (g.scrimAlpha > 0) {
            LinearGradient(0f, 0f, 0f, h, intArrayOf(0, 0, g.scrimAlpha shl 24), null, Shader.TileMode.CLAMP)
        } else null
        val bmp = if (full) g.blur else null
        shaderBitmap = bmp
        bitmapShader = bmp?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    /** Coverage for the layout auditor: 1 when nothing behind can show through. */
    override val auditAlpha: Float get() {
        ensure()
        if (kind == Kind.ITEM) return ((fixedFill ushr 24) and 0xFF) / 255f
        if (kind == Kind.FROSTED && look.opaque) return 1f
        return 1f - look.leak * (1f - resolved.scrimAlpha)
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
        outline.alpha = if (kind == Kind.FROSTED && look.opaque) 1f else 0.5f
    }

    override fun setAlpha(alpha: Int) {
        alphaMul = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaMul

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    /** Opaque only when the look covers what is behind (frosted sample or solid). */
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int =
        if (kind == Kind.FROSTED && look.opaque && alphaMul == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT

    private companion object {
        /** Layers of the feathered text scrim (see [GlassScrim]). */
        const val SCRIM_STEPS = 4

        /** One cached filter per (saturation, lift): looks have a handful of values. */
        private val filters = HashMap<Long, ColorFilter>()

        fun colorBoostFilter(sat: Float, lift: Float): ColorFilter {
            val key = (java.lang.Float.floatToIntBits(sat).toLong() shl 32) or (java.lang.Float.floatToIntBits(lift).toLong() and 0xFFFFFFFFL)
            return filters.getOrPut(key) {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(sat) }
                // Lift toward white: v' = v (1 - lift) + 255 lift.
                val k = 1f - lift
                val o = 255f * lift
                cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
                    k, 0f, 0f, 0f, o, 0f, k, 0f, 0f, o, 0f, 0f, k, 0f, o, 0f, 0f, 0f, 1f, 0f)))
                android.graphics.ColorMatrixColorFilter(cm)
            }
        }
    }
}

/** Circular focusable icon button; white fill with dark icon when focused. */
class RoundButton(context: Context, iconRes: Int, title: String, onClick: () -> Unit) : ImageView(context) {
    private val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    var title: String = title
        private set

    init {
        isFocusable = true
        isClickable = true
        contentDescription = title
        setImageResource(iconRes)
        scaleType = ScaleType.FIT_CENTER
        background = fill
        setOnClickListener { onClick() }
        applyFocus(false)
    }

    fun setIcon(res: Int, newTitle: String) {
        setImageResource(res)
        title = newTitle
        contentDescription = newTitle
    }

    private fun applyFocus(f: Boolean) {
        fill.setColor(if (f) Theme.TEXT else 0x1AFFFFFF)
        imageTintList = ColorStateList.valueOf(if (f) Theme.TEXT_DARK else Theme.TEXT)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        applyFocus(gainFocus)
        Glass.focusScale(this, if (gainFocus) 1.1f else 1f, Motion.ms(TileSpec.FOCUS_MS), GlassBackdrop.fullTier)
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }
}

/** The feathered text scrim's maths (pure, JVM-tested). */
internal object GlassScrim {
    /** Layer i of n sits this fraction of the rim band in: 0.25 .. 1.0. */
    fun insetFraction(i: Int, n: Int): Float = if (n <= 1) 1f else 0.25f + 0.75f * i / (n - 1)

    /** Alpha (0..255) per layer so that n stacked layers compose to [total] (0..255). */
    fun stepAlpha(total: Int, n: Int): Int {
        if (n <= 1 || total <= 0) return total.coerceIn(0, 255)
        val a = total.coerceIn(0, 255) / 255.0
        return Math.round((1.0 - Math.pow(1.0 - a, 1.0 / n)) * 255.0).toInt().coerceIn(0, 255)
    }

    /** What n layers of [step] give where they all overlap (0..255). */
    fun composed(step: Int, n: Int): Int = Math.round((1.0 - Math.pow(1.0 - step / 255.0, n.toDouble())) * 255.0).toInt()
}
