package dev.tvlauncher.browser

/**
 * Pure virtual-mouse logic for D-pad browsing (pixel units; [density] converts the dp constants).
 * Each [move] is one key step: the step starts at [START_DP] and ramps by [RAMP_DP] per repeated
 * step in the same direction up to [MAX_DP]; [release] (key up) resets it. The cursor is clamped
 * to the screen. Pushing against the top or bottom edge asks for a page scroll of
 * [EDGE_SCROLL_DP]; the left and right edges never scroll.
 */
class CursorModel(width: Float, height: Float, private val density: Float = 1f) {
    enum class Dir { UP, DOWN, LEFT, RIGHT }

    /** Result of one step: new position plus a vertical page scroll request (0 = none). */
    data class Step(val x: Float, val y: Float, val scrollY: Float)

    private var maxX = maxOf(0f, width - 1f)
    private var maxY = maxOf(0f, height - 1f)
    var x = width / 2f; private set
    var y = height / 2f; private set
    var speed = START_DP * density; private set
    private var heldDir: Dir? = null

    fun resize(width: Float, height: Float) {
        maxX = maxOf(0f, width - 1f)
        maxY = maxOf(0f, height - 1f)
        x = x.coerceIn(0f, maxX)
        y = y.coerceIn(0f, maxY)
    }

    /** Places the cursor (e.g. restoring state), clamped. */
    fun moveTo(nx: Float, ny: Float) {
        x = nx.coerceIn(0f, maxX)
        y = ny.coerceIn(0f, maxY)
    }

    fun move(dir: Dir): Step {
        if (heldDir != dir) {
            heldDir = dir
            speed = START_DP * density
        }
        val d = speed
        var scroll = 0f
        when (dir) {
            Dir.LEFT -> x = (x - d).coerceAtLeast(0f)
            Dir.RIGHT -> x = (x + d).coerceAtMost(maxX)
            Dir.UP -> {
                val ny = y - d
                if (ny < 0f) scroll = -EDGE_SCROLL_DP * density
                y = ny.coerceAtLeast(0f)
            }
            Dir.DOWN -> {
                val ny = y + d
                if (ny > maxY) scroll = EDGE_SCROLL_DP * density
                y = ny.coerceAtMost(maxY)
            }
        }
        speed = (speed + RAMP_DP * density).coerceAtMost(MAX_DP * density)
        return Step(x, y, scroll)
    }

    fun release() {
        heldDir = null
        speed = START_DP * density
    }

    companion object {
        const val START_DP = 8f
        const val MAX_DP = 40f
        const val RAMP_DP = 3f
        const val EDGE_SCROLL_DP = 120f
    }
}
