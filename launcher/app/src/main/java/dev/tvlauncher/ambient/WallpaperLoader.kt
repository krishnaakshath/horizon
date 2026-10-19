package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Off-main-thread wallpaper production: procedural built-ins and user images. Every result is a
 * single bitmap of exactly the requested size (center-cropped, EXIF orientation applied); the
 * intermediate decode is recycled immediately. Results for cancelled jobs are recycled, never
 * delivered.
 */
object WallpaperLoader {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wallpaper-loader").apply { isDaemon = true; priority = Thread.MIN_PRIORITY + 1 }
    }
    private val main = Handler(Looper.getMainLooper())

    class Job internal constructor() {
        @Volatile var cancelled = false
            private set
        fun cancel() { cancelled = true }
    }

    fun builtIn(id: String, size: WallpaperPlan.Size, accent: Int = BuiltInWallpapers.DEFAULT_ACCENT,
                config: Bitmap.Config = Bitmap.Config.ARGB_8888, done: (Bitmap?) -> Unit): Job =
        submit(done) { BuiltInWallpapers.render(id, size.width, size.height, accent, config) }

    fun file(file: File, size: WallpaperPlan.Size, config: Bitmap.Config = Bitmap.Config.ARGB_8888,
             cover: Boolean = false, done: (Bitmap?) -> Unit): Job =
        submit(done) { decode(file, size.width, size.height, config, cover) }

    /** Runs [work] on the loader thread and delivers on the main thread. */
    fun <T> background(work: () -> T, done: (T) -> Unit) {
        executor.execute {
            val r = work()
            main.post { done(r) }
        }
    }

    private fun submit(done: (Bitmap?) -> Unit, work: () -> Bitmap?): Job {
        val job = Job()
        executor.execute {
            if (job.cancelled) return@execute
            val bmp = try { work() } catch (e: OutOfMemoryError) { null } catch (e: Exception) { null }
            main.post {
                if (job.cancelled) bmp?.recycle() else done(bmp)
            }
        }
        return job
    }

    /** Synchronous decode (call off the main thread). Null on any failure or oversize file. */
    /** [cover]: thumbnail planning (both sides stay >= target when the budget allows). */
    fun decode(file: File, w: Int, h: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888, cover: Boolean = false): Bitmap? {
        if (!file.isFile || file.length() !in 1..WallpaperPlan.MAX_FILE_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val (deg, flip) = orientation(file)
        val swap = deg == 90 || deg == 270
        val srcW = if (swap) bounds.outHeight else bounds.outWidth
        val srcH = if (swap) bounds.outWidth else bounds.outHeight
        val planned = if (cover) SampleSizePlanner.planCover(srcW, srcH, w, h) else SampleSizePlanner.plan(srcW, srcH, w, h)
        val sample = when (val plan = planned) {
            is SamplePlan.Reject -> return null // corrupt, > 120 MP or a side > 65535: never decode
            is SamplePlan.Sample -> plan.n
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            // JPEG/most photos have no alpha: decode the intermediate at half the memory.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.path, opts) ?: return null
        return try { cropInto(decoded, w, h, deg, flip, config) } finally { decoded.recycle() }
    }

    /** Center-crops [src] (after rotation/flip) into a new [w]x[h] bitmap. */
    fun cropInto(src: Bitmap, w: Int, h: Int, deg: Int = 0, flip: Boolean = false,
                 config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val out = Bitmap.createBitmap(max(1, w), max(1, h), config)
        val m = Matrix()
        m.setScale(if (flip) -1f else 1f, 1f)
        m.postRotate(deg.toFloat())
        val r = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        m.mapRect(r)
        m.postTranslate(-r.left, -r.top)
        val scale = max(w / r.width(), h / r.height())
        m.postScale(scale, scale)
        m.postTranslate((w - r.width() * scale) / 2f, (h - r.height() * scale) / 2f)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        return out
    }

    /** EXIF orientation as (degrees, horizontal flip). Missing/unreadable EXIF -> (0, false). */
    private fun orientation(file: File): Pair<Int, Boolean> = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 to true
            ExifInterface.ORIENTATION_ROTATE_180 -> 180 to false
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 to true
            ExifInterface.ORIENTATION_TRANSPOSE -> 90 to true
            ExifInterface.ORIENTATION_ROTATE_90 -> 90 to false
            ExifInterface.ORIENTATION_TRANSVERSE -> 270 to true
            ExifInterface.ORIENTATION_ROTATE_270 -> 270 to false
            else -> 0 to false
        }
    } catch (e: Exception) {
        0 to false
    }
}

/**
 * Main-thread [RecycleGate] for bitmaps: a replaced bitmap is recycled only after at least one
 * more frame interval, so neither the UI thread nor the RenderThread can still be drawing it.
 * Ticks run on a Handler (not the view), so they also complete after the view is detached.
 */
object BitmapRecycler {
    private val main = Handler(Looper.getMainLooper())
    private val gate = RecycleGate<Bitmap> { if (!it.isRecycled) it.recycle() }
    private const val TICK_MS = 50L // > one 60 Hz frame (16.7 ms), also safe at 24/30 Hz
    private val tick = object : Runnable {
        override fun run() {
            gate.frame()
            if (gate.pendingCount > 0) main.postDelayed(this, TICK_MS)
        }
    }

    fun defer(b: Bitmap?) {
        if (b == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { defer(b) }; return }
        gate.defer(b)
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
    }

    /** [b] is being shown again: keep it. */
    fun keep(b: Bitmap) { gate.cancel(b) }
}

/** The user wallpaper folder: app-specific external storage, no storage permission needed. */
object UserWallpapers {
    const val DEVICE_PATH = "/sdcard/Android/data/dev.tvlauncher/files/wallpapers/"
    const val ADB_PUSH = "adb push photo.jpg $DEVICE_PATH"

    fun folder(context: Context): File? = try {
        context.getExternalFilesDir("wallpapers")?.also { if (!it.exists()) it.mkdirs() }
    } catch (e: Exception) {
        null
    }

    /** Accepted images (see [WallpaperPlan.acceptImages]). Disk IO: call off the main thread. */
    fun list(folder: File?): List<File> {
        val files = try { folder?.listFiles()?.filter { it.isFile } } catch (e: SecurityException) { null } ?: return emptyList()
        val byName = files.associateBy { it.name }
        return WallpaperPlan.acceptImages(files.map { WallpaperPlan.Candidate(it.name, it.length()) })
            .mapNotNull { byName[it.name] }
    }
}

/** SharedPreferences "wallpaper_settings" holding one tolerant JSON blob. */
class WallpaperStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stored paths that do not resolve inside the wallpaper folder are ignored (default built-in). */
    fun load(): WallpaperSettings =
        WallpaperSettings.fromJson(prefs.getString(KEY, null)).sanitizedFor(UserWallpapers.folder(app))

    /** Single write path: also turns the in-memory notification feed on/off (off clears it). */
    fun save(settings: WallpaperSettings) {
        prefs.edit().putString(KEY, settings.toJson()).apply()
        NotificationFeed.shared.setEnabled(settings.notificationsEnabled)
    }

    companion object {
        const val PREFS = "wallpaper_settings"
        const val KEY = "settings_json"
    }
}
