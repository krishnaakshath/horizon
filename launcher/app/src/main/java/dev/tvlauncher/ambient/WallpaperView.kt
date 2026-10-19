package dev.tvlauncher.ambient

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import java.io.File
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private fun centerCrop(m: Matrix, b: Bitmap, w: Int, h: Int) {
    val s = max(w.toFloat() / b.width, h.toFloat() / b.height)
    m.setScale(s, s)
    m.postTranslate((w - b.width * s) / 2f, (h - b.height * s) / 2f)
}

/**
 * Full-screen wallpaper layer. Draws the current bitmap (center-cropped, matrix cached), cross-
 * fades to the next one and optionally drifts very slowly (FULL only: translation of the view on
 * a [WallpaperPlan.DRIFT_CYCLE_MS] cycle over a static 4 % overscan). No allocation in onDraw.
 * Bitmaps passed to [show] are owned by the view and recycled when replaced or [release]d.
 */
class WallpaperView(context: Context, private val lite: Boolean) : View(context) {
    private var current: Bitmap? = null
    private var next: Bitmap? = null
    private val curMatrix = Matrix()
    private val nextMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val dimPaint = Paint().apply { color = 0xFF000000.toInt() }
    private var baseColor = 0xFF0B0D12.toInt()
    private var fade: ValueAnimator? = null
    private var drift: ValueAnimator? = null
    private var driftEnabled = false

    fun setBaseColor(color: Int) { baseColor = color; invalidate() }

    /** 0..60 % black over the wallpaper (text legibility / brightness). */
    fun setDim(percent: Int) {
        dimPaint.alpha = (percent.coerceIn(0, WallpaperSettings.MAX_DIM) * 255 / 100)
        invalidate()
    }

    fun show(bitmap: Bitmap, animate: Boolean) {
        fade?.let { it.removeAllListeners(); it.cancel() }
        fade = null
        next?.let { if (it !== bitmap) promote(it) }
        if (current == null || !animate || width == 0) {
            replaceCurrent(bitmap)
            return
        }
        next = bitmap
        centerCrop(nextMatrix, bitmap, width, height)
        fadePaint.alpha = 0
        fade = ValueAnimator.ofInt(0, 255).apply {
            duration = WallpaperPlan.crossfadeMs(lite)
            addUpdateListener { fadePaint.alpha = it.animatedValue as Int; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    next?.let { promote(it) }
                }
            })
            start()
        }
    }

    private fun promote(b: Bitmap) {
        next = null
        replaceCurrent(b)
    }

    private fun replaceCurrent(b: Bitmap) {
        val old = current
        current = b
        if (width > 0) centerCrop(curMatrix, b, width, height)
        if (old != null && old !== b) old.recycle()
        invalidate()
    }

    fun setDrift(enabled: Boolean) {
        driftEnabled = enabled && !lite
        updateDrift()
    }

    private fun updateDrift() {
        val run = driftEnabled && isAttachedToWindow && width > 0
        if (!run) {
            drift?.cancel(); drift = null
            translationX = 0f; translationY = 0f; scaleX = 1f; scaleY = 1f
            return
        }
        if (drift != null) return
        scaleX = OVERSCAN; scaleY = OVERSCAN
        val ax = width * (OVERSCAN - 1f) / 2f * 0.8f
        val ay = height * (OVERSCAN - 1f) / 2f * 0.8f
        drift = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = WallpaperPlan.DRIFT_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = (it.animatedValue as Float) * 2f * PI.toFloat()
                translationX = ax * sin(t)
                translationY = ay * sin(t * 2f) * 0.5f
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        current?.let { centerCrop(curMatrix, it, w, h) }
        next?.let { centerCrop(nextMatrix, it, w, h) }
        drift?.cancel(); drift = null
        updateDrift()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateDrift()
    }

    override fun onDetachedFromWindow() {
        drift?.cancel(); drift = null
        fade?.let { it.removeAllListeners(); it.cancel() }
        fade = null
        next?.let { promote(it) }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(baseColor)
        current?.let { if (!it.isRecycled) canvas.drawBitmap(it, curMatrix, paint) }
        next?.let { if (!it.isRecycled) canvas.drawBitmap(it, nextMatrix, fadePaint) }
        if (dimPaint.alpha > 0) canvas.drawPaint(dimPaint)
    }

    override fun hasOverlappingRendering(): Boolean = false

    /** Recycles everything; the view draws only its base color afterwards. */
    fun release() {
        fade?.let { it.removeAllListeners(); it.cancel() }
        fade = null
        drift?.cancel(); drift = null
        next?.recycle(); next = null
        current?.recycle(); current = null
        invalidate()
    }

    private companion object {
        const val OVERSCAN = 1.04f
    }
}

/**
 * Wallpaper as a plain Drawable (for the home backdrop). Starts as the base color and swaps in
 * the bitmap once [setBitmap] is called; owns and recycles it on [release].
 */
class WallpaperDrawable(private var baseColor: Int) : Drawable() {
    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private var job: WallpaperLoader.Job? = null

    internal fun attachJob(j: WallpaperLoader.Job) { job = j }

    fun setBitmap(b: Bitmap?) {
        val old = bitmap
        bitmap = b
        if (b != null && !bounds.isEmpty) centerCrop(matrix, b, bounds.width(), bounds.height())
        if (old != null && old !== b) old.recycle()
        invalidateSelf()
    }

    val isReady: Boolean get() = bitmap != null

    override fun onBoundsChange(bounds: Rect) {
        bitmap?.let { centerCrop(matrix, it, bounds.width(), bounds.height()) }
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.clipRect(0, 0, bounds.width(), bounds.height())
        canvas.drawColor(baseColor)
        bitmap?.let { if (!it.isRecycled) canvas.drawBitmap(it, matrix, paint) }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    fun release() {
        job?.cancel(); job = null
        setBitmap(null)
    }
}

/**
 * Resolves a [WallpaperSettings] source into bitmaps for a [WallpaperView]: renders built-ins,
 * decodes user images, and runs the slideshow (Handler timer, crossfade). Falls back to the
 * default built-in when a file is missing or the folder is empty.
 */
class WallpaperController(
    private val context: Context,
    private val view: WallpaperView,
    profile: DeviceProfile,
    private val accent: Int = BuiltInWallpapers.DEFAULT_ACCENT
) {
    private val lite = profile.effectsMode == EffectsMode.LITE
    private val size = WallpaperPlan.targetSize(profile.widthPx, profile.heightPx, lite)
    private val main = Handler(Looper.getMainLooper())
    private var job: WallpaperLoader.Job? = null
    private var slides: List<File> = emptyList()
    private var index = -1
    private var step = 0L
    private val seed = System.currentTimeMillis()
    private var source: WallpaperSource? = null
    private var shuffle = false
    private var running = false
    private val advance = object : Runnable {
        override fun run() {
            val s = source as? WallpaperSource.Slideshow ?: return
            showSlide(WallpaperPlan.next(slides, index, shuffle, seed + step++), animate = true)
            main.postDelayed(this, WallpaperPlan.clampInterval(s.intervalSec) * 1000L)
        }
    }

    fun start(settings: WallpaperSettings) {
        stop()
        running = true
        source = settings.source
        shuffle = settings.shuffle
        view.setDim(settings.dimPercent)
        view.setDrift(!lite)
        when (val s = settings.source) {
            is WallpaperSource.BuiltIn -> builtIn(s.id, animate = false)
            is WallpaperSource.UserImage -> loadFile(s.file, animate = false)
            is WallpaperSource.Slideshow -> WallpaperLoader.background({ UserWallpapers.list(s.folder) }) { files ->
                if (!running || source != s) return@background
                slides = files
                if (files.isEmpty()) { builtIn(WallpaperSource.DEFAULT_ID, false); return@background }
                val first = if (shuffle) WallpaperPlan.next(files, 0, true, seed) else 0
                showSlide(first, animate = false)
                main.postDelayed(advance, WallpaperPlan.clampInterval(s.intervalSec) * 1000L)
            }
        }
    }

    fun stop() {
        running = false
        main.removeCallbacks(advance)
        job?.cancel(); job = null
    }

    private fun showSlide(i: Int, animate: Boolean) {
        if (i !in slides.indices) return
        index = i
        loadFile(slides[i], animate)
    }

    private fun builtIn(id: String, animate: Boolean) {
        job?.cancel()
        view.setBaseColor(BuiltInWallpapers.meta(id).baseColor)
        job = WallpaperLoader.builtIn(id, size, accent) { b -> if (b != null) view.show(b, animate) }
    }

    private fun loadFile(f: File, animate: Boolean) {
        job?.cancel()
        job = WallpaperLoader.file(f, size) { b ->
            if (b != null) view.show(b, animate) else if (source !is WallpaperSource.Slideshow) builtIn(WallpaperSource.DEFAULT_ID, false)
        }
    }
}
