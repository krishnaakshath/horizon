package dev.tvlauncher.browser.cursor

import kotlin.math.sqrt

/**
 * The browser pointer on a game controller's left stick: analog speed instead of D-pad steps.
 * Radial [DEADZONE] against drift, then a squared curve (fine control near the centre, fast at
 * full tilt) up to [MAX_DP_PER_SEC], scaled by the pointer speed setting. Pushing on past the top
 * or bottom edge scrolls the page by the overshoot ([edgeScroll]). Pure (pixels, milliseconds).
 */
object StickCursor {
    const val DEADZONE = 0.35f
    const val MAX_DP_PER_SEC = 1100f

    data class Move(val dx: Float, val dy: Float) {
        val isZero: Boolean get() = dx == 0f && dy == 0f
    }

    /** Pixels to move over [dtMs] for stick ([x], [y]); zero inside the deadzone. */
    fun step(x: Float, y: Float, dtMs: Long, density: Float, speedFactor: Float = 1f): Move {
        val m = sqrt(x * x + y * y)
        if (m < DEADZONE || dtMs <= 0) return Move(0f, 0f)
        val n = ((m - DEADZONE) / (1f - DEADZONE)).coerceIn(0f, 1f)
        val speed = n * n * MAX_DP_PER_SEC * density * speedFactor * dtMs / 1000f
        return Move(x / m * speed, y / m * speed)
    }

    /** Vertical page scroll for a move of [dy] from [y] on a screen of height [maxY]: the part past the edge. */
    fun edgeScroll(y: Float, dy: Float, maxY: Float): Float = when {
        dy < 0f && y + dy < 0f -> y + dy
        dy > 0f && y + dy > maxY -> y + dy - maxY
        else -> 0f
    }

    /** True while the stick is outside the deadzone (the pointer loop keeps running). */
    fun active(x: Float, y: Float): Boolean = sqrt(x * x + y * y) >= DEADZONE
}
