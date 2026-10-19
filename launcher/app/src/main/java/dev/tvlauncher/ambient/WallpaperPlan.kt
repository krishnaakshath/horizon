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

    /** Picker tiles per row: as many [cellPx] cells as fit [innerPx], between 2 and [max]. */
    fun tileColumns(innerPx: Int, cellPx: Int, max: Int): Int =
        if (cellPx <= 0) max else (innerPx / cellPx).coerceIn(2, max)

    fun bitmapBytes(w: Int, h: Int): Long = w.toLong() * h * 4

    fun crossfadeMs(lite: Boolean): Long = if (lite) 400L else 800L

    fun clampInterval(sec: Int): Int = sec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
}

sealed class SamplePlan {
    object Reject : SamplePlan() { override fun toString() = "Reject" }
    data class Sample(val n: Int) : SamplePlan()
}

/**
 * Decode budget for user images (decompression-bomb guard). Rejects bad or huge sources
 * (> [DEFAULT_MAX_SRC_PIXELS] = 120 MP or a side > 65535) using Long math, otherwise picks the smallest
 * power-of-two sample whose decoded pixel count fits [maxDecodedPixels] (default 4x target).
 * The smallest such n is also the least downsampling, so the result never gets needlessly
 * smaller than the target. Used for full-screen wallpapers, slideshow and picker thumbnails.
 *
 * Caveat: the 120 MP source cap bounds memory, not time. Bounds decoding is cheap, but a 100+ MP
 * image is still fully parsed by the codec even when subsampled, which can take seconds on a slow
 * TV SoC. It runs on the low-priority loader thread (UI stays responsive); the 8 MB file cap keeps
 * such files rare.
 */
object SampleSizePlanner {
    const val DEFAULT_MAX_SRC_PIXELS = 120_000_000L
    const val MAX_SIDE = 65_535
    private const val FALLBACK_BUDGET = 4L * 1920 * 1080
    private const val MAX_SAMPLE = 1 shl 16

    fun plan(
        srcW: Int, srcH: Int, targetW: Int, targetH: Int,
        maxSrcPixels: Long = DEFAULT_MAX_SRC_PIXELS,
        maxDecodedPixels: Long = 4L * targetW * targetH
    ): SamplePlan {
        if (srcW <= 0 || srcH <= 0 || srcW > MAX_SIDE || srcH > MAX_SIDE) return SamplePlan.Reject
        if (srcW.toLong() * srcH.toLong() > maxSrcPixels) return SamplePlan.Reject
        val budget = if (maxDecodedPixels > 0) maxDecodedPixels else FALLBACK_BUDGET
        var n = 1
        while (n < MAX_SAMPLE && (srcW / n).toLong() * (srcH / n).toLong() > budget) n *= 2
        return SamplePlan.Sample(n)
    }

    /** Decoded-pixel budget for cover (center-crop) decodes such as thumbnails: 16x the target. */
    fun coverBudget(targetW: Int, targetH: Int): Long = 16L * targetW.coerceAtLeast(1) * targetH.coerceAtLeast(1)

    /**
     * Thumbnail planning: the largest power-of-two sample that keeps BOTH sides >= the target
     * (so a center-crop never upscales), then doubled further only if the decoded pixels exceed
     * [maxDecodedPixels]; on extreme aspect ratios the budget wins.
     */
    fun planCover(
        srcW: Int, srcH: Int, targetW: Int, targetH: Int,
        maxSrcPixels: Long = DEFAULT_MAX_SRC_PIXELS,
        maxDecodedPixels: Long = coverBudget(targetW, targetH)
    ): SamplePlan {
        if (plan(srcW, srcH, targetW, targetH, maxSrcPixels) is SamplePlan.Reject) return SamplePlan.Reject
        val tw = targetW.coerceAtLeast(1); val th = targetH.coerceAtLeast(1)
        var n = 1
        while (n < MAX_SAMPLE && srcW / (n * 2) >= tw && srcH / (n * 2) >= th) n *= 2
        while (n < MAX_SAMPLE && (srcW / n).toLong() * (srcH / n).toLong() > maxDecodedPixels) n *= 2
        return SamplePlan.Sample(n)
    }
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
