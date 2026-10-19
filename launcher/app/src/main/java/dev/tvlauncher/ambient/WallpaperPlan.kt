package dev.tvlauncher.ambient

import kotlin.math.max
import kotlin.math.min

/** Pure wallpaper rules: slideshow order, accepted user files, decode sizes, timings. */
object WallpaperPlan {
    data class Candidate(val name: String, val sizeBytes: Long)
    data class Size(val width: Int, val height: Int)

    const val MAX_FILES = 24
    const val MAX_FILE_BYTES = 8L * 1024 * 1024
    const val MIN_INTERVAL_SEC = 30
    const val MAX_INTERVAL_SEC = 600
    const val DEFAULT_INTERVAL_SEC = 60
    const val DRIFT_CYCLE_MS = 90_000L
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Next slideshow index after [currentIndex]. Sequential wraps around; shuffle picks a
     * deterministic (per [seed]) index that is never the current one. -1 when [files] is empty.
     */
    fun <T> next(files: List<T>, currentIndex: Int, shuffle: Boolean, seed: Long): Int {
        val n = files.size
        if (n == 0) return -1
        if (n == 1) return 0
        if (currentIndex !in 0 until n) return 0
        if (!shuffle) return (currentIndex + 1) % n
        // Mix seed and index (splitmix64 finalizer), then skip 1..n-1 positions ahead.
        var z = seed * -0x61c8864680b583ebL + currentIndex
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        val skip = 1 + ((z ushr 1) % (n - 1)).toInt()
        return (currentIndex + skip) % n
    }

    /** jpg/jpeg/png/webp, 1 byte..8 MB, no dot-files; sorted by name, at most 24. */
    fun acceptImages(files: List<Candidate>): List<Candidate> = files
        .filter { c ->
            !c.name.startsWith(".") && c.sizeBytes in 1..MAX_FILE_BYTES &&
                c.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
        }
        .sortedBy { it.name.lowercase() }
        .take(MAX_FILES)

    /** Largest power-of-two sample size that keeps the decoded image at least the target size. */
    fun inSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return 1
        var s = 1
        while (srcW / (s * 2) >= dstW && srcH / (s * 2) >= dstH) s *= 2
        return s
    }

    /** Pre-render size: screen size in landscape, capped at 1920x1080 (FULL) or 1280x720 (LITE). */
    fun targetSize(screenW: Int, screenH: Int, lite: Boolean): Size {
        val cap = if (lite) Size(1280, 720) else Size(1920, 1080)
        val w = max(screenW, screenH)
        val h = min(screenW, screenH)
        if (w <= 0 || h <= 0) return cap
        if (w <= cap.width && h <= cap.height) return Size(w, h)
        val scale = min(cap.width.toFloat() / w, cap.height.toFloat() / h)
        return Size((w * scale + 0.5f).toInt(), (h * scale + 0.5f).toInt())
    }

    fun thumbSize(): Size = Size(320, 180)

    fun bitmapBytes(w: Int, h: Int): Long = w.toLong() * h * 4

    fun crossfadeMs(lite: Boolean): Long = if (lite) 400L else 800L

    fun clampInterval(sec: Int): Int = sec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
}

/** Deterministic star field for the "nebula" wallpaper: x, y, radius, alpha per star. */
object StarField {
    const val STRIDE = 4

    fun countFor(w: Int, h: Int): Int = (w.toLong() * h / 6_000L).toInt().coerceIn(0, 400)

    fun generate(seed: Long, count: Int, w: Int, h: Int): FloatArray {
        val out = FloatArray(count * STRIDE)
        var s = seed xor 0x5DEECE66DL
        fun nextFloat(): Float {
            s = (s * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            return (s ushr 24).toInt().toFloat() / (1 shl 24)
        }
        for (i in 0 until count) {
            out[i * 4] = nextFloat() * w
            out[i * 4 + 1] = nextFloat() * h
            val r = nextFloat()
            out[i * 4 + 2] = 0.4f + r * r * 1.8f // mostly tiny, a few brighter
            out[i * 4 + 3] = 0.15f + nextFloat() * 0.85f
        }
        return out
    }
}
