package dev.tvlauncher.appsettings

import kotlin.math.roundToInt

/** An integer rectangle (px); right/bottom exclusive. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun intersects(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun contains(o: Box) = o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom
}

/**
 * Pure geometry of the Settings screen: an overscan-safe sidebar (about a third of the width)
 * and the glass pane, with a gap so the focus scale of one never touches the other.
 */
data class SettingsLayout(
    val sidebar: Box,
    val pane: Box,
    /** Where rows go inside the pane (pane padding and, if any, the scrollbar removed). */
    val paneContent: Box,
    val scrollbar: Box?,
    val marginH: Int,
    val marginV: Int,
    val gap: Int,
    val rowMinHeight: Int,
    val rowGap: Int
) {
    /** The trailing value text may take at most this much of a row. */
    fun valueMaxWidth(rowWidth: Int): Int = (rowWidth * VALUE_MAX_FRACTION).toInt()

    companion object {
        const val MARGIN_H_DP = 48
        const val MARGIN_V_DP = 27
        const val SIDEBAR_FRACTION = 0.32f
        const val SIDEBAR_MIN_FRACTION = 0.30f
        const val SIDEBAR_MAX_FRACTION = 0.36f
        const val GAP_DP = 24
        const val PANE_PAD_DP = 24
        const val SCROLLBAR_DP = 3
        const val SCROLLBAR_GAP_DP = 8
        const val ROW_MIN_DP = 60
        const val ROW_GAP_DP = 8
        const val VALUE_MAX_FRACTION = 0.35f

        fun compute(widthPx: Int, heightPx: Int, density: Float, hasScrollbar: Boolean): SettingsLayout {
            fun dp(v: Int) = (v * density).roundToInt()
            val mh = dp(MARGIN_H_DP)
            val mv = dp(MARGIN_V_DP)
            val sideW = (widthPx * SIDEBAR_FRACTION).roundToInt()
                .coerceIn((widthPx * SIDEBAR_MIN_FRACTION).toInt() + 1, (widthPx * SIDEBAR_MAX_FRACTION).toInt())
            val sidebar = Box(mh, mv, mh + sideW, heightPx - mv)
            val gap = dp(GAP_DP)
            val pane = Box(sidebar.right + gap, mv, widthPx - mh, heightPx - mv)
            val pad = dp(PANE_PAD_DP)
            val bar = if (hasScrollbar) {
                val r = pane.right - dp(SCROLLBAR_GAP_DP)
                Box(r - dp(SCROLLBAR_DP), pane.top + pad, r, pane.bottom - pad)
            } else null
            // The thumb lives in the pane's right padding; rows never reach it.
            val contentRight = minOf(pane.right - pad, bar?.let { it.left - dp(SCROLLBAR_GAP_DP) } ?: Int.MAX_VALUE)
            val content = Box(pane.left + pad, pane.top + pad, contentRight, pane.bottom - pad)
            return SettingsLayout(sidebar, pane, content, bar, mh, mv, gap, dp(ROW_MIN_DP), dp(ROW_GAP_DP))
        }
    }
}

/** Thin scroll indicator math: thumb (top, height) within a [track], or null when nothing scrolls. */
object ScrollIndicator {
    fun thumb(range: Int, extent: Int, offset: Int, track: Int, minThumb: Int): Pair<Int, Int>? {
        if (range <= extent || range <= 0 || track <= 0) return null
        val h = (track.toLong() * extent / range).toInt().coerceIn(minOf(minThumb, track), track)
        val maxOffset = range - extent
        val top = ((track - h).toLong() * offset.coerceIn(0, maxOffset) / maxOffset).toInt()
        return top to h
    }
}
