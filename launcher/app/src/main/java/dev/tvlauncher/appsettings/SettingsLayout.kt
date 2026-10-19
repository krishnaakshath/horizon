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
    val gap: Int
) {
    /** Horizontal inset of the pane's content (header text and rows) from the pane's left edge. */
    val paneInset: Int get() = paneContent.left - pane.left

    /** Where sidebar entries go: the card minus its padding and the list's side padding. */
    fun sidebarEntries(density: Float): Box {
        val p = Math.round((SIDEBAR_PAD_DP + SIDEBAR_LIST_PAD_DP) * density)
        val v = Math.round(SIDEBAR_PAD_DP * density)
        return Box(sidebar.left + p, sidebar.top + v, sidebar.right - p, sidebar.bottom - v)
    }

    /** Where rows go in the pane: the page column's padding inside the pane. */
    fun rows(density: Float): Box = Box(
        pane.left + Math.round(PANE_PAD_DP * density), pane.top,
        pane.right - Math.round(PAGE_PAD_END_DP * density), pane.bottom
    )

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
        /** Inner padding of the frosted sidebar card. */
        const val SIDEBAR_PAD_DP = 14
        /** Side padding of the sidebar list inside the card: room for an entry's focus scale. */
        const val SIDEBAR_LIST_PAD_DP = 8
        /** Right padding of a page's row column (also holds the scroll thumb). */
        const val PAGE_PAD_END_DP = PANE_PAD_DP + 4

        fun compute(widthPx: Int, heightPx: Int, density: Float, hasScrollbar: Boolean): SettingsLayout {
            fun dp(v: Int) = (v * density).roundToInt()
            val mh = dp(MARGIN_H_DP)
            val mv = dp(MARGIN_V_DP)
            val min = (widthPx * SIDEBAR_MIN_FRACTION).toInt() + 1
            val max = (widthPx * SIDEBAR_MAX_FRACTION).toInt()
            val wanted = (widthPx * SIDEBAR_FRACTION).roundToInt()
            // Degenerate widths (a few px) can make min > max; never throw from coerceIn then.
            val sideW = if (min > max) wanted else wanted.coerceIn(min, max)
            val sidebar = Box(mh, mv, mh + sideW, heightPx - mv)
            val gap = dp(GAP_DP)
            val pane = Box(sidebar.right + gap, mv, widthPx - mh, heightPx - mv)
            val pad = dp(PANE_PAD_DP)
            val bar = if (hasScrollbar) {
                    // Centred in the page column's right gutter (inside the card padding, right of every row).
                val c = pane.right - dp(PAGE_PAD_END_DP) / 2
                val l = c - dp(SCROLLBAR_DP) / 2
                Box(l, pane.top + pad, l + dp(SCROLLBAR_DP), pane.bottom - pad)
            } else null
            // The thumb lives in the pane's right padding; rows never reach it.
            val contentRight = minOf(pane.right - pad, bar?.let { it.left - dp(SCROLLBAR_GAP_DP) } ?: Int.MAX_VALUE)
            val content = Box(pane.left + pad, pane.top + pad, contentRight, pane.bottom - pad)
            return SettingsLayout(sidebar, pane, content, bar, mh, mv, gap)
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

/**
 * Horizontal budget of one [SettingsRow]: icon chip, title column (weight 1), value and controls.
 * The value may take at most [SettingsLayout.VALUE_MAX_FRACTION] of the row and never so much that
 * the title keeps less than [TITLE_MIN_FRACTION]; long text wraps (2 lines) or ellipsizes inside
 * its own column, so the columns cannot overlap.
 */
object RowMetrics {
    const val TITLE_MIN_FRACTION = 0.30f

    /** Max width (px) of the value text in a row [rowWidth] wide whose other fixed parts take [fixed] px. */
    fun valueMax(rowWidth: Int, fixed: Int): Int {
        val byFraction = (rowWidth * SettingsLayout.VALUE_MAX_FRACTION).toInt()
        val byTitle = rowWidth - fixed - (rowWidth * TITLE_MIN_FRACTION).toInt()
        return minOf(byFraction, byTitle).coerceAtLeast(0)
    }
}

/**
 * Sizes a row of [count] round swatches so the whole strip, including the focus scale
 * ([focusScale]) of the last swatch, fits [available] px. Prefers [size]/[gap]; shrinks both
 * (gap first, to half) when the pane is narrow. Returns (size, gap).
 */
object SwatchFit {
    fun fit(available: Int, count: Int, size: Int, gap: Int, focusScale: Float): Pair<Int, Int> {
        if (count <= 0 || available <= 0) return size to gap
        fun width(s: Int, g: Int) = count * s + (count - 1) * g + Math.ceil(s * (focusScale - 1f).toDouble()).toInt()
        if (width(size, gap) <= available) return size to gap
        val minGap = gap / 2
        if (width(size, minGap) <= available) {
            var g = gap
            while (g > minGap && width(size, g) > available) g--
            return size to g
        }
        var s = size
        while (s > 1 && width(s, minGap) > available) s--
        return s to minGap
    }
}

/**
 * Vertical rhythm of the sidebar card, on the 8 dp grid: a fixed title row, then the clipped list
 * (it starts below the title, so nothing can scroll under it). Entries are [ENTRY_DP] tall,
 * headers [HEADER_DP], each later group starts [GROUP_GAP_DP] below the previous entry.
 */
object SidebarMetrics {
    const val TITLE_DP = 48
    const val TITLE_GAP_DP = 8
    const val ENTRY_DP = 48
    const val HEADER_DP = 40
    const val GROUP_GAP_DP = 8
    const val ENTRY_GAP_DP = 0
    /** Right padding of entries and headers: chevrons and labels end on the same column. */
    const val END_PAD_DP = 16

    /** Title row and list region inside a sidebar card [card] (px), with [pad] px card padding. */
    fun regions(card: Box, padPx: Int, density: Float): Pair<Box, Box> {
        val title = Box(card.left + padPx, card.top + padPx, card.right - padPx, card.top + padPx + Math.round(TITLE_DP * density))
        val list = Box(title.left, title.bottom + Math.round(TITLE_GAP_DP * density), title.right, card.bottom - padPx)
        return title to list
    }

    /** Top offsets (dp) of each entry in a list of [headers] flags (true = group header). */
    fun tops(headers: List<Boolean>): List<Int> {
        var y = 0
        return headers.mapIndexed { i, h ->
            if (h && i > 0) y += GROUP_GAP_DP
            val top = y
            y += (if (h) HEADER_DP else ENTRY_DP) + ENTRY_GAP_DP
            top
        }
    }
}

/**
 * The pane's vertical stack: a FIXED header (breadcrumb on sub-pages only, heading, optional
 * description) and below it the page body. The body is the only scrolling part and clips to its
 * own bounds (SettingsPage.clipChildren = true), so a row, scrolled or not, can never draw over the
 * header. Heights are measured px; gaps are dp.
 */
object PaneStack {
    const val TOP_DP = 22
    const val CRUMB_GAP_DP = 6
    const val HEADING_GAP_DP = 6
    const val DESC_GAP_DP = 12

    data class Result(val header: Box, val body: Box)

    fun layout(pane: Box, density: Float, crumbH: Int, headingH: Int, descH: Int): Result {
        fun dp(v: Int) = Math.round(v * density)
        var y = pane.top + dp(TOP_DP)
        if (crumbH > 0) y += crumbH + dp(CRUMB_GAP_DP)
        y += headingH + dp(HEADING_GAP_DP)
        if (descH > 0) y += descH
        y += dp(DESC_GAP_DP)
        val headerBottom = minOf(y, pane.bottom)
        return Result(Box(pane.left, pane.top, pane.right, headerBottom), Box(pane.left, headerBottom, pane.right, pane.bottom))
    }

    /** What of a row at [row] is drawn: its intersection with the clipping [body] (null when hidden). */
    fun visible(row: Box, body: Box): Box? {
        val b = Box(maxOf(row.left, body.left), maxOf(row.top, body.top), minOf(row.right, body.right), minOf(row.bottom, body.bottom))
        return if (b.width > 0 && b.height > 0) b else null
    }
}
