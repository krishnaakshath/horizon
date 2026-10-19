package dev.tvlauncher.browser.cursor

import kotlin.math.hypot

/**
 * Magnetic snapping: inside [radius] the cursor eases toward a target's center by a fraction
 * that grows with [Magnet] strength and with closeness, capped at maxStep per tick; within
 * [DEAD_ZONE] of the center it stops (no jitter).
 */
object SnapMath {
    const val DEAD_ZONE = 0.75f

    data class Offset(val dx: Float, val dy: Float) { companion object { val ZERO = Offset(0f, 0f) } }

    fun radiusDp(m: Magnet): Float = when (m) { Magnet.OFF -> 0f; Magnet.LOW -> 12f; Magnet.MED -> 20f; Magnet.HIGH -> 32f }

    private fun gain(m: Magnet): Float = when (m) { Magnet.OFF -> 0f; Magnet.LOW -> 0.15f; Magnet.MED -> 0.3f; Magnet.HIGH -> 0.5f }

    fun pull(cx: Float, cy: Float, tx: Float, ty: Float, radius: Float, magnet: Magnet, maxStep: Float = 4f): Offset {
        val g = gain(magnet)
        if (g <= 0f || radius <= 0f) return Offset.ZERO
        val dx = tx - cx; val dy = ty - cy
        val d = hypot(dx, dy)
        if (d > radius || d <= DEAD_ZONE) return Offset.ZERO
        // Stronger when closer: fraction falls off linearly to 40% at the rim.
        val frac = g * (1f - 0.6f * (d / radius))
        var step = d * frac
        if (step > maxStep) step = maxStep
        if (d - step < DEAD_ZONE) step = d // land exactly on the center
        return Offset(dx / d * step, dy / d * step)
    }
}
