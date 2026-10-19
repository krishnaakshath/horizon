package dev.tvlauncher.browser.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Fetches each site's own icon at runtime (never bundled): <origin>/apple-touch-icon.png, then
 * icons declared in the page HEAD (first 64 KB), then /favicon.ico. HTTPS only, 4 s per request,
 * <= 3 redirects, <= 256 KB, image/ content types. Results are downscaled, cached in memory
 * (24 entries) and on disk (cacheDir/favicons, 4 MB, 30 days).
 */
class FaviconLoader(context: Context) {
    class Icon(val bitmap: Bitmap, val color: Int?, val fullBleed: Boolean)

    private val dir = File(context.cacheDir, "favicons")
    private val mem = LruCache<String, Icon>(24)
    private val failedAt = HashMap<String, Long>()
    private val waiting = HashMap<String, MutableList<(Icon?) -> Unit>>()
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newFixedThreadPool(2, ThreadFactory { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "favicons").apply { isDaemon = true }
    })

    init { exec.execute { prune() } }

    fun peek(pageUrl: String): Icon? = FaviconUrlCandidates.origin(pageUrl)?.let { mem.get(it) }

    /** Calls [cb] on the main thread with the icon, or null when none could be loaded. */
    fun load(pageUrl: String, cb: (Icon?) -> Unit) {
        val origin = FaviconUrlCandidates.origin(pageUrl) ?: return cb(null)
        mem.get(origin)?.let { return cb(it) }
        failedAt[origin]?.let { if (System.currentTimeMillis() - it < FAIL_TTL_MS) return cb(null) else failedAt.remove(origin) }
        waiting[origin]?.let { it.add(cb); return }
        waiting[origin] = mutableListOf(cb)
        exec.execute {
            val icon = try { fromDisk(origin) ?: fromNetwork(pageUrl, origin) } catch (t: Throwable) { null }
            main.post {
                if (icon != null) mem.put(origin, icon) else failedAt[origin] = System.currentTimeMillis()
                waiting.remove(origin)?.forEach { it(icon) }
            }
        }
    }

    private fun file(origin: String): File {
        val d = MessageDigest.getInstance("SHA-1").digest(origin.toByteArray())
        return File(dir, d.joinToString("") { "%02x".format(it) } + ".png")
    }

    private fun fromDisk(origin: String): Icon? {
        val f = file(origin)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > TTL_MS) return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        return describe(b)
    }

    private fun fromNetwork(pageUrl: String, origin: String): Icon? {
        val tried = HashSet<String>()
        var best: Bitmap? = null
        fun consider(list: List<String>): Boolean {
            for (u in list) {
                if (!tried.add(u)) continue
                val b = fetchBitmap(u) ?: continue
                if (best == null || b.width > best!!.width) best = b
                if (b.width >= GOOD_PX) return true
            }
            return false
        }
        // 1) apple-touch-icon.png at the origin
        if (!consider(FaviconUrlCandidates.build(pageUrl, null).take(1))) {
            // 2) icons declared in the HEAD, 3) favicon.ico
            val head = Http.default.get(origin + "/", HtmlIconParser.MAX_HTML, TIMEOUT_MS, { it.contains("html", true) }, truncateOk = true)
                ?.let { r -> String(r.bytes, Charsets.UTF_8).let { s -> s.substring(0, s.indexOf("</head>", ignoreCase = true).takeIf { it > 0 } ?: s.length) } }
            consider(FaviconUrlCandidates.build(pageUrl, head))
        }
        val b = best ?: return null
        try {
            dir.mkdirs()
            file(origin).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            prune() // keep the disk cache within 4 MB after every write
        } catch (e: Exception) { }
        return describe(b)
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val r = Http.default.get(url, MAX_BYTES, TIMEOUT_MS, { it.startsWith("image/", true) && !it.contains("svg", true) }) ?: return null
        // Bounds first (always), then a sample factor from the LONGEST edge; huge/elongated images are rejected.
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(r.bytes, 0, r.bytes.size, o)
        val plan = IconDecodePlanner.plan(o.outWidth, o.outHeight) as? IconDecodePlanner.Plan.Sample ?: return null
        val b = BitmapFactory.decodeByteArray(r.bytes, 0, r.bytes.size, BitmapFactory.Options().apply { inSampleSize = plan.n }) ?: return null
        val (w, h) = IconDecodePlanner.fit(b.width, b.height, TARGET_PX)
        if (w == b.width && h == b.height) return b
        return Bitmap.createScaledBitmap(b, w, h, true).also { if (it !== b) b.recycle() }
    }

    private fun describe(b: Bitmap): Icon {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        val px = IntArray(24 * 24)
        s.getPixels(px, 0, 24, 0, 0, 24, 24)
        if (s !== b) s.recycle()
        val corners = intArrayOf(b.getPixel(0, 0), b.getPixel(b.width - 1, 0), b.getPixel(0, b.height - 1), b.getPixel(b.width - 1, b.height - 1))
        return Icon(b, IconColors.dominant(px), corners.all { (it ushr 24) > 200 })
    }

    @Synchronized private fun prune() {
        val files = dir.listFiles() ?: return
        val entries = files.map { CachePolicy.Entry(it.name, it.length(), it.lastModified()) }
        val drop = CachePolicy.evict(entries, MAX_DISK, TTL_MS, System.currentTimeMillis()).toSet()
        files.filter { it.name in drop }.forEach { it.delete() }
    }

    /** Clears memory and disk caches (synchronous disk delete so a following clear is complete). */
    fun clear() {
        mem.evictAll(); failedAt.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Low memory: drop decoded icons (disk cache stays). */
    fun trimMemory() = mem.evictAll()

    fun shutdown() = exec.shutdownNow()

    private companion object {
        const val TIMEOUT_MS = 4000
        const val MAX_BYTES = 256 * 1024
        const val MAX_DISK = 4L * 1024 * 1024
        const val TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val TARGET_PX = 96
        const val GOOD_PX = 64
        const val FAIL_TTL_MS = 10L * 60 * 1000
    }
}
