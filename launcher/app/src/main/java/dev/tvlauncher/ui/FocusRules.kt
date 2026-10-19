package dev.tvlauncher.ui

/** Pure focus-restoration rules so they can be unit-tested on the JVM. */
object FocusRules {
    /**
     * Index to focus after the item at [removed] left a list that now has [newSize] items.
     * Prefers the right neighbor (which now sits at the same index), else the left neighbor,
     * else null when the list is empty. An out-of-range [removed] is clamped into range.
     */
    fun nextFocusIndexAfterRemoval(removed: Int, newSize: Int): Int? {
        if (newSize <= 0) return null
        return removed.coerceIn(0, newSize - 1)
    }
}
