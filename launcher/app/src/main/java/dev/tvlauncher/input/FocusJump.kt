package dev.tvlauncher.input

/** Pure arithmetic behind L1/R1 (page) and L2/R2 (quick scroll) focus jumps. */
object FocusJump {
    /** Upper bound so a tiny item in a huge list never makes one press cross everything. */
    const val MAX_STEPS = 12

    /**
     * How many items one page press moves: the items that fit in [viewport] minus one (the last
     * visible item becomes the first), at least 1, at most [MAX_STEPS]. Unknown sizes give 1.
     */
    fun pageSteps(viewport: Int, item: Int): Int {
        if (viewport <= 0 || item <= 0) return 1
        return (viewport / item - 1).coerceIn(1, MAX_STEPS)
    }

    /** Index [steps] away from [current] in a list of [count], clamped; -1 for an empty list. */
    fun target(current: Int, count: Int, steps: Int, forward: Boolean): Int {
        if (count <= 0) return -1
        val t = if (forward) current + steps else current - steps
        return t.coerceIn(0, count - 1)
    }

    /**
     * The next (or previous) page in the order the sidebar lists them, [ordered] being every page
     * id of every group. Null at either end (no wrap) or when [current] is unknown and there is
     * nothing to go to.
     */
    fun adjacent(ordered: List<String>, current: String?, forward: Boolean): String? {
        if (ordered.isEmpty()) return null
        val i = ordered.indexOf(current)
        if (i < 0) return if (forward) ordered.first() else ordered.last()
        val j = if (forward) i + 1 else i - 1
        return ordered.getOrNull(j)
    }

    /** Minimum gap between repeated page / scroll jumps while L1/R1/L2/R2 is held. */
    const val REPEAT_GAP_MS = 220L

    fun repeatAllowed(lastAt: Long, now: Long): Boolean = now - lastAt >= REPEAT_GAP_MS
}
