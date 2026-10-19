package dev.tvlauncher.ui

/**
 * Dwell logic for the tile quick-strip. A tile that keeps focus for [DWELL_MS] with no D-pad
 * activity gets the strip; any focus move or D-pad press restarts the wait. Nothing shows while
 * Spotlight is open. Time is passed in so this runs on the JVM.
 */
class QuickStripState(private val dwellMs: Long = DWELL_MS) {
    /** Adapter position of the focused tile, or [NONE]. */
    var position = NONE
        private set
    var visible = false
        private set
    /** True while focus is inside the visible strip. */
    var inStrip = false
        private set
    private var since = 0L

    var spotlightOpen = false
        set(v) {
            field = v
            if (v) { visible = false; inStrip = false }
        }

    fun onTileFocused(pos: Int, now: Long) {
        if (inStrip && visible && pos == position) {
            inStrip = false // UP/BACK from the strip to its own tile keeps it shown
            return
        }
        inStrip = false
        visible = false
        position = pos
        since = now
    }

    /** A D-pad press on the tile that did not move focus. */
    fun onDpad(now: Long) {
        visible = false
        since = now
    }

    fun onEnterStrip() {
        if (visible) inStrip = true
    }

    fun onFocusLeftShelf() {
        position = NONE
        visible = false
        inStrip = false
    }

    /** Returns true exactly once, when the strip should appear. */
    fun poll(now: Long): Boolean {
        if (visible || spotlightOpen || position == NONE || now - since < dwellMs) return false
        visible = true
        return true
    }

    fun hide() {
        visible = false
        inStrip = false
    }

    companion object {
        const val NONE = -1
        const val DWELL_MS = 700L
    }

    object Geometry {
        /**
         * Left of the strip ([w] wide) centered under [centerX], kept inside the overscan-safe area:
         * [safeStart]/[safeEnd] are the safe margins measured from the host's own edges.
         */
        fun stripLeft(centerX: Float, w: Float, hostW: Float, safeStart: Float, safeEnd: Float): Float {
            val lo = maxOf(0f, safeStart)
            val hi = maxOf(lo, hostW - maxOf(0f, safeEnd) - w)
            return (centerX - w / 2f).coerceIn(lo, hi)
        }
    }
}
