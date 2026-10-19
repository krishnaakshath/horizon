package dev.tvlauncher.browser.net

import kotlin.math.max
import kotlin.math.min

/** Pure color helpers for site tiles: dominant hue of an icon and stable letter-tile colors. */
object IconColors {
    val PALETTE = intArrayOf(
        0xFF4F7CFF.toInt(), 0xFFE5484D.toInt(), 0xFF30A46C.toInt(), 0xFFF5A524.toInt(),
        0xFF8E4EC6.toInt(), 0xFF12A594.toInt(), 0xFFE54666.toInt(), 0xFF3E63DD.toInt(),
        0xFFD6409F.toInt(), 0xFF0091FF.toInt()
    )
    private const val BUCKETS = 12
    private const val MAX_SAMPLES = 32 * 32

    /** Most common saturated hue (averaged), ignoring transparent/near-black/gray pixels; null if none. */
    fun dominant(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val step = max(1, (pixels.size + MAX_SAMPLES - 1) / MAX_SAMPLES)
        val n = IntArray(BUCKETS); val r = LongArray(BUCKETS); val g = LongArray(BUCKETS); val b = LongArray(BUCKETS)
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]; i += step
            if ((c ushr 24) < 128) continue
            val cr = (c shr 16) and 0xFF; val cg = (c shr 8) and 0xFF; val cb = c and 0xFF
            val mx = max(cr, max(cg, cb)); val mn = min(cr, min(cg, cb))
            if (mx < 40 || (mx - mn).toFloat() / mx < 0.25f) continue
            val d = (mx - mn).toFloat()
            var h = when (mx) { cr -> ((cg - cb) / d) % 6f; cg -> (cb - cr) / d + 2f; else -> (cr - cg) / d + 4f } * 60f
            if (h < 0) h += 360f
            val k = (h / (360f / BUCKETS)).toInt().coerceIn(0, BUCKETS - 1)
            n[k]++; r[k] += cr.toLong(); g[k] += cg.toLong(); b[k] += cb.toLong()
        }
        var best = -1
        for (k in 0 until BUCKETS) if (n[k] > 0 && (best < 0 || n[k] > n[best])) best = k
        if (best < 0) return null
        val c = n[best]
        return (0xFF shl 24) or ((r[best] / c).toInt() shl 16) or ((g[best] / c).toInt() shl 8) or (b[best] / c).toInt()
    }

    fun letterColor(host: String): Int {
        var h = 0
        for (ch in host.lowercase()) h = h * 31 + ch.code
        return PALETTE[Math.floorMod(h, PALETTE.size)]
    }

    fun letterFor(host: String): Char =
        host.removePrefix("www.").firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'

    /** Linear ARGB blend from [a] (t=0) to [b] (t=1). */
    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int): Int { val x = (a shr s) and 0xFF; val y = (b shr s) and 0xFF; return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255) }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
