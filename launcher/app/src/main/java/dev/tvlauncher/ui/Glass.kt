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
import dev.tvlauncher.room.RoomTheme
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
    // Legacy constants (kept for callers outside this package).
    const val FILL_FULL = 0xB8151922.toInt()
    const val FILL_LITE = 0xE6151922.toInt()
    const val BORDER = 0x33FFFFFF
    const val HIGHLIGHT = GlassTokens.SHEEN_TOP
    /**
     * Legacy near-opaque panel fills. The frosted card that replaced them is opaque by
     * construction (it paints the backdrop sample first), so content never reads through it.
     */
    const val PANEL_FULL = 0xF5151922.toInt()
    const val PANEL_LITE = 0xFC151922.toInt()

    /** Frosted card on [view] (sets its background; FULL: elevation shadow). Returns the drawable. */
    fun frost(view: View, full: Boolean, radiusDp: Int = GlassTokens.RADIUS_PANEL): GlassDrawable {
        val d = GlassDrawable(view.context, GlassDrawable.Kind.FROSTED, radiusDp, full)
        d.host = view
        view.background = d
        if (full) {
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.elevation = view.context.dp(GlassTokens.SHADOW_DP).toFloat()
        }
        GlassBackdrop.track(view)
        return d
    }

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
            cornerRadius = context.dp(radiusDp).toFloat()
            if (stroke != 0) setStroke(context.dp(strokeDp), stroke)
        }

    /** Repaints [view] whenever the backdrop sample changes (adaptive tint, blur). */
    fun track(view: View) = GlassBackdrop.track(view)

    /** Compatibility: a floating panel (frosted, host-less: sampled from the window origin). */
    fun panel(context: Context, full: Boolean, radiusDp: Int): Drawable =
        GlassDrawable(context, GlassDrawable.Kind.FROSTED, radiusDp, full)

    /** Compatibility: a small translucent surface (now [clear] glass). */
    fun surface(context: Context, full: Boolean, radiusDp: Int): Drawable = clear(context, full, radiusDp)
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

    fun isOwner(o: Any) = owner === o

    /** A new settled backdrop from [from]; false when nothing changed. A changed sample drops the old blur. */
    fun publish(from: Any, s: IntArray, scrim: Int, w: Int, h: Int): Boolean {
        val same = owner === from && w == width && h == height && scrim == scrimAlpha &&
            s[0] == stops[0] && s[1] == stops[1] && s[2] == stops[2]
        if (same) return false
        owner = from
        System.arraycopy(s, 0, stops, 0, 3)
        scrimAlpha = scrim; width = w; height = h
        blur = null
        retint(null)
        return true
    }

    /** The blurred copy for the current sample of [from]; [brightest] is its brightest pixel. */
    fun setBlur(from: Any, bitmap: Bitmap, brightest: Int) {
        if (owner !== from) return
        blur = bitmap
        retint(brightest)
    }

    /** [from] went away: forget its sample. The bitmap is left to the GC (never recycle() a bitmap a RenderNode may still hold). */
    fun release(from: Any) {
        if (owner !== from) return
        owner = null
        blur = null
        version++
    }

    private fun retint(blurBrightest: Int?) {
        var worst = GlassTokens.brightest(stops)
        if (blurBrightest != null) {
            pair[0] = worst; pair[1] = blurBrightest
            worst = GlassTokens.brightest(pair)
        }
        tint = GlassTokens.tint(RoomTheme.palette, worst)
        clearTint = GlassTokens.clearTint(RoomTheme.palette, worst)
        version++
        for (i in hosts.indices.reversed()) hosts.getOrNull(i)?.invalidate()
    }
}

/**
 * The glass drawable (see [Glass]). [Kind.FROSTED] uses [host] to align its backdrop sample
 * with the window; without one it samples from the window origin.
 */
class GlassDrawable(context: Context, val kind: Kind, radiusDp: Int, private val full: Boolean) : Drawable() {
    enum class Kind { FROSTED, CLEAR, ITEM }

    var host: View? = null
    /** ITEM fill (ARGB); ignored by the other kinds. */
    var fixedFill: Int = GlassTokens.ROW_REST

    private val radius = context.dp(radiusDp).toFloat()
    private val edgePx = GlassTokens.edgePx(context.resources.displayMetrics.density).toFloat()
    private val rect = RectF()
    private val edgeRect = RectF()
    private val base = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = edgePx }
    private val matrix = Matrix()
    private val loc = IntArray(2)
    private var alphaMul = 255
    private var sampleVersion = -1
    private var gradient: Shader? = null
    private var scrimShader: Shader? = null
    private var bitmapShader: BitmapShader? = null
    private var shaderBitmap: Bitmap? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        val top = bounds.top.toFloat()
        sheen.shader = LinearGradient(0f, top, 0f, top + h * GlassTokens.SHEEN_FRACTION,
            GlassTokens.SHEEN_TOP, 0, Shader.TileMode.CLAMP)
        val edgeTop = if (kind == Kind.ITEM) GlassTokens.EDGE_TOP_ITEM else GlassTokens.EDGE_TOP
        edge.shader = LinearGradient(0f, top, 0f, top + h, edgeTop, GlassTokens.EDGE_BOTTOM, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        rect.set(bounds)
        if (kind == Kind.FROSTED) drawSample(canvas)
        val tint = when (kind) {
            Kind.ITEM -> fixedFill
            Kind.CLEAR -> GlassBackdrop.clearTint
            Kind.FROSTED -> GlassBackdrop.tint
        }
        fill.color = tint
        fill.alpha = (((tint ushr 24) and 0xFF) * alphaMul) / 255
        canvas.drawRoundRect(rect, radius, radius, fill)
        sheen.alpha = alphaMul
        canvas.drawRoundRect(rect, radius, radius, sheen)
        val half = edgePx / 2f
        edgeRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
        edge.alpha = alphaMul
        val r = (radius - half).coerceAtLeast(0f)
        canvas.drawRoundRect(edgeRect, r, r, edge)
    }

    /** The backdrop behind the card: shaders live in window coordinates, shifted by the host's position. */
    private fun drawSample(canvas: Canvas) {
        val g = GlassBackdrop
        if (sampleVersion != g.version) rebuildSample()
        layoutLocation(host, loc)
        val dx = -loc[0].toFloat()
        val dy = -loc[1].toFloat()
        val bs = bitmapShader
        val bmp = shaderBitmap
        base.alpha = alphaMul
        if (bs != null && bmp != null && g.width > 0) {
            matrix.setScale(g.width / bmp.width.toFloat(), g.height / bmp.height.toFloat())
            matrix.postTranslate(dx, dy)
            bs.setLocalMatrix(matrix)
            base.shader = bs
            canvas.drawRoundRect(rect, radius, radius, base)
            return
        }
        val gs = gradient ?: return
        matrix.setTranslate(dx, dy)
        gs.setLocalMatrix(matrix)
        base.shader = gs
        canvas.drawRoundRect(rect, radius, radius, base)
        val ss = scrimShader ?: return
        ss.setLocalMatrix(matrix)
        scrim.shader = ss
        scrim.alpha = alphaMul
        canvas.drawRoundRect(rect, radius, radius, scrim)
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

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
        outline.alpha = if (kind == Kind.FROSTED) 1f else 0.5f
    }

    override fun setAlpha(alpha: Int) {
        alphaMul = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaMul

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    /** FROSTED is opaque (it paints the backdrop itself); the others are translucent. */
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int =
        if (kind == Kind.FROSTED && alphaMul == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
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
        val s = if (gainFocus) 1.1f else 1f
        animate().cancel()
        animate().scaleX(s).scaleY(s).setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }
}
