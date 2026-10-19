package dev.tvlauncher.ui

/** Pure scroll math for D-pad focus-follow. All coordinates share one space (scroll content). */
object ScrollTarget {
    /**
     * Returns the scroll delta (negative = up) that makes the row fully visible with [margin],
     * or 0 if it already is. A row taller than the viewport is aligned to its top.
     */
    fun compute(rowTop: Int, rowBottom: Int, viewportTop: Int, viewportBottom: Int, margin: Int): Int {
        if (rowTop - margin < viewportTop) return rowTop - margin - viewportTop
        if (rowBottom + margin > viewportBottom) {
            val down = rowBottom + margin - viewportBottom
            val maxDown = rowTop - margin - viewportTop
            return minOf(down, maxDown)
        }
        return 0
    }
}
