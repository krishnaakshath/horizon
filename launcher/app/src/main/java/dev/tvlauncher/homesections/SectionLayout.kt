package dev.tvlauncher.homesections

import dev.tvlauncher.homelayout.ScreenDp
import dev.tvlauncher.ui.TileSpec

/**
 * Sizing of the composed home, in dp. Pure. The stack lives inside the overscan-safe area
 * ([SAFE_H_DP] x [SAFE_V_DP]) under the top bar and keeps a minimum hero strip for the focus row's
 * text, so rows never overlap the top bar or each other. LITE on a 720p-class screen shows at
 * most two sections at once; the rest are reached by moving down (the stack scrolls a page).
 */
object SectionLayout {
    const val SAFE_H_DP = 48
    const val SAFE_V_DP = 27
    const val TOP_BAR_DP = 48
    const val HERO_MIN_DP = 72
    const val TITLE_DP = 22
    const val ROW_GAP_DP = 8
    /** Compact icons: half-size cards. */
    const val COMPACT_SCALE = 0.5f

    /** Card width for [size] at the user's tile scale. */
    fun tileWidthDp(s: HomeSection, tileScale: Float): Int = (TileSpec.WIDTH_DP * cardScale(s, tileScale)).toInt()

    /** The TileSpec scale for a section's cards (style and size on top of the user's tile size). */
    fun cardScale(s: HomeSection, tileScale: Float): Float = tileScale * s.size.scale * when (s.style) {
        SectionStyle.COMPACT -> COMPACT_SCALE
        SectionStyle.BIG_CARDS -> 1.25f
        else -> 1f
    }

    fun tileHeightDp(s: HomeSection, tileScale: Float): Int = tileWidthDp(s, tileScale) * 9 / 16

    /** Lines of tiles in one section ([SectionStyle.TWO_ROWS] and [SectionStyle.GRID] use two). */
    fun lines(s: HomeSection): Int = when (s.style) {
        SectionStyle.TWO_ROWS, SectionStyle.GRID -> 2
        else -> 1
    }

    /** Height of section [s] with its title, tiles and focus padding. */
    fun rowHeightDp(s: HomeSection, tileScale: Float): Int =
        (if (s.showTitle) TITLE_DP else 0) +
            lines(s) * (tileHeightDp(s, tileScale) + 2 * TileSpec.PAD_DP + TileSpec.LABEL_GAP_DP + TileSpec.LABEL_HEIGHT_DP) + ROW_GAP_DP

    /** Tiles fully visible across the safe width. */
    fun tilesAcross(s: HomeSection, tileScale: Float, screen: ScreenDp): Int {
        val usable = screen.widthDp - 2 * SAFE_H_DP
        val step = tileWidthDp(s, tileScale) + TileSpec.GAP_DP
        return maxOf(1, (usable + TileSpec.GAP_DP) / step)
    }

    /** Height left for section rows: the safe area minus the top bar and the minimum hero. */
    fun budgetDp(screen: ScreenDp): Int = screen.heightDp - 2 * SAFE_V_DP - TOP_BAR_DP - HERO_MIN_DP

    /** Sections on screen at once: LITE on a 720p-class screen 2, otherwise up to 4. */
    fun maxVisible(lite: Boolean, screen: ScreenDp): Int = if (lite && screen.heightDp <= 720) 2 else 4

    /**
     * How many of [rows] (top to bottom, starting at [from]) fit on one page. Always at least one,
     * so a single section is never cut.
     */
    fun pageSize(rows: List<HomeSection>, from: Int, tileScale: Float, screen: ScreenDp, lite: Boolean): Int {
        val cap = maxVisible(lite, screen)
        var used = 0
        var n = 0
        for (i in from until rows.size) {
            val h = rowHeightDp(rows[i], tileScale)
            if (n > 0 && (used + h > budgetDp(screen) || n >= cap)) break
            used += h
            n++
        }
        return maxOf(1, n)
    }

    /** First row of the page that shows row [focused] (pages start at 0 and follow [pageSize]). */
    fun pageStart(rows: List<HomeSection>, focused: Int, tileScale: Float, screen: ScreenDp, lite: Boolean): Int {
        var start = 0
        while (start < rows.size) {
            val n = pageSize(rows, start, tileScale, screen, lite)
            if (focused < start + n) return start
            start += n
        }
        return 0
    }

    /** Items a section shows: its cap, and for compact/row styles never more than [available]. */
    fun itemCount(s: HomeSection, available: Int): Int = minOf(available, s.maxItems).coerceAtLeast(0)
}

/** Where a D-pad key on a composed home goes. */
sealed class SectionMove {
    data class Focus(val row: Int, val column: Int) : SectionMove()
    object TopBar : SectionMove()
    object Spotlight : SectionMove()
    /** Not a section key (LEFT/RIGHT inside a row): the row handles it. */
    object Pass : SectionMove()
}

/**
 * D-pad between sections. Pure. UP/DOWN keeps the column the owner was on (clamped to the target
 * row's length, so the nearest tile); UP from the first row goes to the top bar, DOWN from the
 * last to Spotlight. Rows with no items are skipped.
 */
object SectionNav {
    enum class Key { UP, DOWN, LEFT, RIGHT }

    fun move(counts: List<Int>, row: Int, column: Int, key: Key): SectionMove = when (key) {
        Key.LEFT, Key.RIGHT -> SectionMove.Pass
        Key.UP -> next(counts, row, column, -1) ?: SectionMove.TopBar
        Key.DOWN -> next(counts, row, column, +1) ?: SectionMove.Spotlight
    }

    private fun next(counts: List<Int>, row: Int, column: Int, dir: Int): SectionMove? {
        var r = row + dir
        while (r in counts.indices) {
            if (counts[r] > 0) return SectionMove.Focus(r, column.coerceIn(0, counts[r] - 1))
            r += dir
        }
        return null
    }
}

/** Which app-row sections the home renders around the main Apps shelf. Pure. */
object SectionRows {
    /**
     * The enabled app-row sections of [s] (Apps included, in order) that fit one screen: a window
     * that always holds Apps, at most [SectionLayout.maxVisible] rows, within the height budget.
     * Classic gives just Apps.
     */
    fun shown(s: HomeSections, tileScale: Float, screen: ScreenDp, lite: Boolean): List<HomeSection> {
        val rows = s.enabled.filter { it.type.appRow }
        val apps = rows.indexOfFirst { it.type == SectionType.APPS }
        if (apps < 0) return rows.take(SectionLayout.pageSize(rows, 0, tileScale, screen, lite))
        val cap = SectionLayout.maxVisible(lite, screen)
        var lo = apps
        var hi = apps
        var used = SectionLayout.rowHeightDp(rows[apps], tileScale)
        val budget = SectionLayout.budgetDp(screen)
        while (hi - lo + 1 < cap) {
            // Sections above Apps first (they lead the home), then those below.
            val cand = when {
                lo > 0 -> lo - 1
                hi < rows.size - 1 -> hi + 1
                else -> break
            }
            val h = SectionLayout.rowHeightDp(rows[cand], tileScale)
            if (used + h > budget) break
            used += h
            if (cand < lo) lo = cand else hi = cand
        }
        return rows.subList(lo, hi + 1)
    }
}
