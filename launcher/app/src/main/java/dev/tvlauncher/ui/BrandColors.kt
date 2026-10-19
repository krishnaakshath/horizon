package dev.tvlauncher.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Accent colors per app. Pure Kotlin (colors are packed ARGB ints) so it is JVM-testable.
 * Known brands map to their exact color; anything else uses the dominant hue of its art.
 */
object BrandColors {
    const val NEUTRAL = 0xFF3A3F4B.toInt()
    /** Apple TV graphite; [backdropAccent] lifts it to a light silver for the backdrop. */
    const val APPLE_TV = 0xFF1C1C1E.toInt()

    private val KNOWN = mapOf(
        "com.netflix.ninja" to 0xFFE50914.toInt(),
        "com.google.android.youtube.tv" to 0xFFFF0000.toInt(),
        "com.google.android.youtube.tvmusic" to 0xFFFF0000.toInt(),
        "com.amazon.amazonvideo.livingroom" to 0xFF00A8E1.toInt(),
        "com.apple.atve.androidtv.appletv" to APPLE_TV,
        "com.disney.disneyplus" to 0xFF113CCF.toInt(),
        "in.startv.hotstar" to 0xFF0C1B5E.toInt(),
        "com.spotify.tv.android" to 0xFF1DB954.toInt(),
        "com.plexapp.android" to 0xFFE5A00D.toInt(),
        "com.android.vending" to 0xFF01875F.toInt(),
        "com.android.tv.settings" to NEUTRAL,
        "com.android.settings" to NEUTRAL
    )

    private const val MAX_SAMPLES = 32 * 32
    private const val HUE_BUCKETS = 12
    private const val SILVER = 0xFF9AA3B5.toInt()

    fun known(pkg: String): Int? = KNOWN[pkg]

    fun forApp(pkg: String, artPixels: IntArray?): Int =
        KNOWN[pkg] ?: artPixels?.let { dominantColor(it) } ?: NEUTRAL

    /**
     * Most common saturated hue in [pixels], averaged. Large inputs are sampled down to at most
     * 32x32 points. Transparent, near-black, near-white and grayish pixels are ignored; if
     * nothing qualifies the neutral slate is returned.
     */
    fun dominantColor(pixels: IntArray): Int {
        if (pixels.isEmpty()) return NEUTRAL
        val step = max(1, (pixels.size + MAX_SAMPLES - 1) / MAX_SAMPLES)
        val count = IntArray(HUE_BUCKETS)
        val sumR = LongArray(HUE_BUCKETS)
        val sumG = LongArray(HUE_BUCKETS)
        val sumB = LongArray(HUE_BUCKETS)
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            i += step
            if ((c ushr 24) < 128) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            if (mx < 40) continue // near-black
            val sat = (mx - mn).toFloat() / mx
            if (sat < 0.25f) continue // gray / near-white
            val bucket = (hue(r, g, b, mx, mn) / (360f / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
            count[bucket]++
            sumR[bucket] += r.toLong(); sumG[bucket] += g.toLong(); sumB[bucket] += b.toLong()
        }
        var best = -1
        for (k in 0 until HUE_BUCKETS) if (count[k] > 0 && (best < 0 || count[k] > count[best])) best = k
        if (best < 0) return NEUTRAL
        val n = count[best]
        return rgb((sumR[best] / n).toInt(), (sumG[best] / n).toInt(), (sumB[best] / n).toInt())
    }

    /** True when white text reads better than black text on [color] (WCAG contrast). */
    fun onColorTextIsLight(color: Int): Boolean {
        val l = luminance(color)
        val withWhite = 1.05 / (l + 0.05)
        val withBlack = (l + 0.05) / 0.05
        return withWhite >= withBlack
    }

    /** Very dark brands (e.g. graphite) would vanish into the near-black backdrop; lift them. */
    fun backdropAccent(color: Int): Int {
        val mx = max((color shr 16) and 0xFF, max((color shr 8) and 0xFF, color and 0xFF))
        return if (mx < 0x50) blend(color, SILVER, 0.75f) else color
    }

    /** Linear ARGB interpolation from [a] (t=0) to [b] (t=1). */
    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun luminance(color: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((color shr 16) and 0xFF) + 0.7152 * lin((color shr 8) and 0xFF) +
            0.0722 * lin(color and 0xFF)
    }

    private fun hue(r: Int, g: Int, b: Int, mx: Int, mn: Int): Float {
        val d = (mx - mn).toFloat()
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return if (h < 0) h + 360f else h
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Tiny per-package memo so art is sampled at most once per app per process. */
    class Cache {
        private val map = HashMap<String, Int>()

        @Synchronized
        fun get(pkg: String, pixels: () -> IntArray?): Int =
            map.getOrPut(pkg) { known(pkg) ?: forApp(pkg, pixels()) }

        @Synchronized
        fun peek(pkg: String): Int? = map[pkg]
    }
}
