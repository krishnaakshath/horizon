package dev.tvlauncher.watch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache

/**
 * Small LRU of decoded poster thumbnails, bounded by bytes ([ThumbRules.cacheBytes]). Decoding
 * only opens content:// and android.resource:// URIs ([ThumbRules.canOpen]) and is done on the
 * caller's (background) thread; a failure is remembered so a broken URI is not retried each focus.
 */
class ThumbCache(context: Context, private val lite: Boolean) {
    private val resolver = context.applicationContext.contentResolver
    private val failed = HashSet<String>()
    private val cache = object : LruCache<String, Bitmap>(ThumbRules.cacheBytes(lite)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun peek(uri: String): Bitmap? = cache.get(uri)

    /** Background thread: the cached or freshly decoded bitmap, or null. */
    fun load(uri: String?, cardWidthPx: Int): Bitmap? {
        if (!ThumbRules.canOpen(uri)) return null
        val u = uri!!
        synchronized(this) {
            cache.get(u)?.let { return it }
            if (u in failed) return null
        }
        val bmp = decode(u, ThumbRules.decodeWidth(cardWidthPx, lite))
        synchronized(this) {
            if (bmp != null) cache.put(u, bmp) else if (failed.size < 256) failed.add(u)
        }
        return bmp
    }

    private fun decode(uri: String, targetW: Int): Bitmap? = try {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS) null
        else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = ThumbRules.sampleSize(bounds.outWidth, bounds.outHeight, targetW)
                inPreferredConfig = if (lite) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            val raw = resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
            raw?.let { scaleDown(it, targetW) }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun scaleDown(b: Bitmap, targetW: Int): Bitmap {
        if (b.width <= targetW * 5 / 4) return b
        val h = (b.height.toLong() * targetW / b.width).toInt().coerceAtLeast(1)
        val s = Bitmap.createScaledBitmap(b, targetW, h, true)
        if (s !== b) b.recycle()
        return s
    }

    @Synchronized
    fun trim() {
        cache.evictAll()
        failed.clear()
    }

    private companion object {
        /** A poster bigger than this (e.g. a 8K image) is not decoded at all. */
        const val MAX_SOURCE_PIXELS = 16_000_000L
    }
}
