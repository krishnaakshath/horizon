package dev.tvlauncher.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/**
 * Tab previews: at most 3 decoded bitmaps in memory, JPEG files in [dir] on disk. Memory can be
 * dropped at any time ([trim]); [load] lazily re-decodes missing previews (RGB_565) off-thread.
 */
class ThumbCache(private val dir: File) {
    private val mem = LruCache<Long, Bitmap>(3)
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "thumbs").apply { isDaemon = true } }

    private fun file(id: Long) = File(dir, "$id.jpg")

    fun put(id: Long, bmp: Bitmap) {
        mem.put(id, bmp)
        run {
            try {
                dir.mkdirs()
                file(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            } catch (e: Exception) { }
        }
    }

    fun remove(id: Long) {
        mem.remove(id)
        run { file(id).delete() }
    }

    fun load(ids: List<Long>, cb: (Map<Long, Bitmap>) -> Unit) {
        val have = HashMap<Long, Bitmap>()
        val missing = ArrayList<Long>()
        ids.forEach { id -> mem.get(id)?.let { have[id] = it } ?: missing.add(id) }
        if (missing.isEmpty()) return cb(have)
        val ok = run {
            val loaded = HashMap<Long, Bitmap>()
            missing.forEach { id ->
                val f = file(id)
                if (f.exists()) BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                })?.let { loaded[id] = it }
            }
            main.post {
                loaded.forEach { (id, b) -> mem.put(id, b) }
                cb(have + loaded)
            }
        }
        if (!ok) cb(have)
    }

    /** Low memory: drop decoded previews (files stay). */
    fun trim() = mem.evictAll()

    fun clearAll() {
        mem.evictAll()
        run { dir.deleteRecursively() }
    }

    fun shutdown() = exec.shutdown()

    private fun run(block: () -> Unit): Boolean = try { exec.execute(block); true } catch (e: Exception) { false }
}
