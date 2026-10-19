package dev.tvlauncher.ambient

import kotlin.math.abs

/**
 * Burn-in protection: a deterministic pseudo-random walk of the whole ambient content, one small
 * step per [INTERVAL_MS], bounded to +-[maxDp] on each axis. Every step moves at least one axis;
 * a step that would cross the bound bounces back instead, so the walk never stalls at an edge.
 * The layout reserves [maxDp] of slack inside the safe margins, so content never leaves them.
 */
class PixelShift(private val maxDp: Int = AmbientLayoutModel.SHIFT_DP, seed: Long) {
    var dx = 0
        private set
    var dy = 0
        private set
    private var state = (seed xor 0x2545F4914F6CDD1DL).let { if (it == 0L) 1L else it }

    fun step() {
        var ax: Int
        var ay: Int
        do {
            ax = nextDelta()
            ay = nextDelta()
        } while (ax == 0 && ay == 0)
        dx = bounce(dx, ax)
        dy = bounce(dy, ay)
    }

    fun dxPx(density: Float): Int = Math.round(dx * density)
    fun dyPx(density: Float): Int = Math.round(dy * density)

    private fun bounce(v: Int, d: Int): Int {
        val n = v + d
        return if (abs(n) <= maxDp) n else v - d
    }

    /** -STEP..STEP from an xorshift64 generator. */
    private fun nextDelta(): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return ((x ushr 33) % (2 * STEP + 1)).toInt() - STEP
    }

    companion object {
        const val INTERVAL_MS = 60_000L
        private const val STEP = 2
        const val MAX_STEP_DP = 4
    }
}

/** Content dimming: 60 % after 2 min of ambient, 35 % at night (23:00-06:00) when enabled. */
object AmbientDimming {
    const val IDLE_DIM_AFTER_MS = 120_000L
    const val IDLE_ALPHA = 0.6f
    const val NIGHT_ALPHA = 0.35f

    fun isNight(hourOfDay: Int): Boolean = hourOfDay >= 23 || hourOfDay < 6

    fun contentAlpha(ambientElapsedMs: Long, hourOfDay: Int, nightDimEnabled: Boolean): Float {
        var a = if (ambientElapsedMs >= IDLE_DIM_AFTER_MS) IDLE_ALPHA else 1f
        if (nightDimEnabled && isNight(hourOfDay)) a = minOf(a, NIGHT_ALPHA)
        return a
    }

    /** Delay until the 2-minute dim applies, or -1 when it already has. */
    fun msUntilIdleDim(ambientElapsedMs: Long): Long =
        if (ambientElapsedMs >= IDLE_DIM_AFTER_MS) -1L else IDLE_DIM_AFTER_MS - ambientElapsedMs
}
