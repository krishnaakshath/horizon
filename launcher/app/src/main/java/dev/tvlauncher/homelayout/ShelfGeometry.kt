package dev.tvlauncher.homelayout

/**
 * Which app sits at each adapter position for a [ListKind], and the visual rows used for D-pad
 * moves. Built once per list change (never per frame); lookups are array reads.
 *
 * - ROW: positions are the apps in order, then the "Get more apps" footer.
 * - BANDS: a two-span horizontal grid fills column by column (position = column * 2 + row), so
 *   the top row (favorites) and the bottom row (every other app, then the footer) interleave;
 *   the shorter row is padded with [EMPTY] slots.
 * - GRID: rows of [lanes] columns, in order, footer last.
 */
class ShelfGeometry private constructor(
    val kind: ListKind,
    /** Per adapter position: an app index, [FOOTER] or [EMPTY]. */
    val slots: IntArray,
    /** Visual rows of focusable adapter positions, top to bottom. */
    val rows: List<IntArray>,
    /** Rows a page jump moves (grid) and columns a page jump moves (bands). */
    val pageRows: Int,
    val pageColumns: Int
) {
    private val rowOf = IntArray(slots.size) { -1 }
    private val colOf = IntArray(slots.size) { -1 }
    private val positionOfApp: IntArray

    init {
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, p -> rowOf[p] = r; colOf[p] = c } }
        val apps = slots.count { it >= 0 }
        positionOfApp = IntArray(apps)
        slots.forEachIndexed { p, s -> if (s >= 0) positionOfApp[s] = p }
    }

    val itemCount: Int get() = slots.size
    val appCount: Int get() = positionOfApp.size

    /** Adapter position of the footer, or -1. */
    val footerPosition: Int = slots.indexOf(FOOTER)

    fun slotAt(position: Int): Int = if (position in slots.indices) slots[position] else EMPTY

    fun positionOf(appIndex: Int): Int = if (appIndex in positionOfApp.indices) positionOfApp[appIndex] else -1

    fun rowOf(position: Int): Int = if (position in rowOf.indices) rowOf[position] else -1

    fun columnOf(position: Int): Int = if (position in colOf.indices) colOf[position] else -1

    companion object {
        const val EMPTY = -1
        const val FOOTER = -2

        /**
         * [apps] apps of which the first [favorites] are favorites (they lead the arranged list),
         * plus the footer when [footer]. [lanes] is the column count for a grid.
         */
        fun build(kind: ListKind, apps: Int, favorites: Int, footer: Boolean, lanes: Int, pageRows: Int, pageColumns: Int): ShelfGeometry {
            val n = apps.coerceAtLeast(0)
            val withFooter = footer && n > 0
            val entries = IntArray(n + if (withFooter) 1 else 0) { if (it < n) it else FOOTER }
            return when (kind) {
                ListKind.ROW -> ShelfGeometry(kind, entries, if (entries.isEmpty()) emptyList() else listOf(positions(entries.size)), 1, pageColumns)
                ListKind.GRID -> {
                    val cols = lanes.coerceAtLeast(1)
                    val rows = (entries.indices step cols).map { start -> IntArray(minOf(cols, entries.size - start)) { start + it } }
                    ShelfGeometry(kind, entries, rows, pageRows.coerceAtLeast(1), cols)
                }
                ListKind.BANDS -> bands(entries, n, favorites, pageRows, pageColumns)
            }
        }

        /** Rows on top: the favorites; with none (or only favorites) the apps are split in half. */
        fun topRowCount(apps: Int, favorites: Int): Int =
            if (favorites in 1 until apps) favorites else (apps + 1) / 2

        private fun bands(entries: IntArray, apps: Int, favorites: Int, pageRows: Int, pageColumns: Int): ShelfGeometry {
            if (entries.isEmpty()) return ShelfGeometry(ListKind.BANDS, entries, emptyList(), pageRows, pageColumns)
            val topCount = topRowCount(apps, favorites)
            val top = entries.copyOfRange(0, topCount)
            val bottom = entries.copyOfRange(topCount, entries.size)
            val columns = maxOf(top.size, bottom.size)
            val slots = IntArray(columns * 2) { EMPTY }
            val topPos = IntArray(top.size)
            val bottomPos = IntArray(bottom.size)
            for (c in 0 until columns) {
                if (c < top.size) { slots[c * 2] = top[c]; topPos[c] = c * 2 }
                if (c < bottom.size) { slots[c * 2 + 1] = bottom[c]; bottomPos[c] = c * 2 + 1 }
            }
            // A trailing empty bottom slot in the last column is not needed by the layout manager.
            val trimmed = if (slots.isNotEmpty() && slots.last() == EMPTY) slots.copyOf(slots.size - 1) else slots
            val rows = listOf(topPos, bottomPos).filter { it.isNotEmpty() }
            return ShelfGeometry(ListKind.BANDS, trimmed, rows, pageRows, pageColumns.coerceAtLeast(1))
        }

        private fun positions(n: Int) = IntArray(n) { it }
    }
}

/** D-pad and L1/R1 keys a shelf layout can handle itself. */
enum class NavKey { LEFT, RIGHT, UP, DOWN, PAGE_PREV, PAGE_NEXT }

/** What a key on a tile should do. */
sealed class NavResult {
    /** Focus the tile at this adapter position. */
    data class Move(val position: Int) : NavResult()
    /** Up off the first row: the top bar (the framework's own focus search finds it). */
    object ExitUp : NavResult() { override fun toString() = "ExitUp" }
    /** Down off the last row: the quick-strip or Spotlight, as on the single row. */
    object ExitDown : NavResult() { override fun toString() = "ExitDown" }
    /** Consume the key; focus stays (the first or last tile). */
    object Stay : NavResult() { override fun toString() = "Stay" }
    /** Leave the key to the framework (the single row keeps its native behavior). */
    object Pass : NavResult() { override fun toString() = "Pass" }
}

/**
 * Launcher-grid focus rules. LEFT/RIGHT walk a row and wrap to the end of the previous row or
 * the start of the next one; UP/DOWN keep the column, clamped to a shorter row; L1/R1 jump a page
 * (a grid by [ShelfGeometry.pageRows] rows, two rows by [ShelfGeometry.pageColumns] tiles). The
 * single row passes every key, so it behaves exactly as before.
 */
object ShelfNav {
    fun move(g: ShelfGeometry, position: Int, key: NavKey): NavResult {
        if (g.kind == ListKind.ROW) return NavResult.Pass
        val r = g.rowOf(position)
        if (r < 0) return NavResult.Pass
        val c = g.columnOf(position)
        val row = g.rows[r]
        val last = g.rows.size - 1
        return when (key) {
            NavKey.RIGHT -> when {
                c + 1 < row.size -> NavResult.Move(row[c + 1])
                r < last -> NavResult.Move(g.rows[r + 1][0])
                else -> NavResult.Stay
            }
            NavKey.LEFT -> when {
                c > 0 -> NavResult.Move(row[c - 1])
                r > 0 -> NavResult.Move(g.rows[r - 1].last())
                else -> NavResult.Stay
            }
            NavKey.UP -> if (r > 0) NavResult.Move(at(g, r - 1, c)) else NavResult.ExitUp
            NavKey.DOWN -> if (r < last) NavResult.Move(at(g, r + 1, c)) else NavResult.ExitDown
            NavKey.PAGE_NEXT, NavKey.PAGE_PREV -> {
                val forward = key == NavKey.PAGE_NEXT
                val target = if (g.kind == ListKind.GRID) {
                    val nr = (if (forward) r + g.pageRows else r - g.pageRows).coerceIn(0, last)
                    at(g, nr, c)
                } else {
                    val nc = (if (forward) c + g.pageColumns else c - g.pageColumns).coerceIn(0, row.size - 1)
                    row[nc]
                }
                if (target == position) NavResult.Stay else NavResult.Move(target)
            }
        }
    }

    /**
     * Grid paging: the adapter position that should sit at the top of the list so the page holding
     * [position] is shown whole (pages are [ShelfGeometry.pageRows] rows). -1 for other kinds.
     */
    fun pageAnchor(g: ShelfGeometry, position: Int): Int {
        if (g.kind != ListKind.GRID) return -1
        val r = g.rowOf(position)
        if (r < 0) return -1
        return g.rows[(r / g.pageRows) * g.pageRows][0]
    }

    private fun at(g: ShelfGeometry, r: Int, c: Int): Int {
        val row = g.rows[r]
        return row[c.coerceAtMost(row.size - 1)]
    }
}
