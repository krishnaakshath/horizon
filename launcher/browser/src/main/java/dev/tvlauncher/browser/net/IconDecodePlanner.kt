package dev.tvlauncher.browser.net

/**
 * Decides how to decode a downloaded icon from its bounds (inJustDecodeBounds first, always):
 * reject empty, tiny, absurdly large or very elongated images; otherwise sample by the LONGEST
 * edge so the decoded bitmap is at most [maxEdge] and [maxPixels].
 */
object IconDecodePlanner {
    sealed class Plan {
        object Reject : Plan() { override fun toString() = "Reject" }
        data class Sample(val n: Int) : Plan()
    }

    const val MIN_EDGE = 16
    const val MAX_SOURCE_EDGE = 8192

    fun plan(w: Int, h: Int, maxEdge: Int = 256, maxPixels: Long = 1024L * 1024, maxAspect: Double = 4.0): Plan {
        if (w <= 0 || h <= 0) return Plan.Reject
        val longEdge = maxOf(w, h)
        val shortEdge = minOf(w, h)
        if (shortEdge < MIN_EDGE || longEdge > MAX_SOURCE_EDGE) return Plan.Reject
        if (longEdge.toDouble() / shortEdge > maxAspect) return Plan.Reject
        var n = 1
        while (longEdge / n > maxEdge) n *= 2
        val decoded = (w.toLong() / n) * (h.toLong() / n)
        if (decoded > maxPixels) return Plan.Reject
        return Plan.Sample(n)
    }

    /** Size fitting inside max x max, preserving aspect; never upscales. */
    fun fit(w: Int, h: Int, max: Int): Pair<Int, Int> {
        if (w <= max && h <= max) return w to h
        return if (w >= h) max to maxOf(1, (h.toLong() * max / w).toInt()) else maxOf(1, (w.toLong() * max / h).toInt()) to max
    }
}
