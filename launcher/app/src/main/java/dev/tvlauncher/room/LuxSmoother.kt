package dev.tvlauncher.room

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Exponential moving average of lux, in the log domain (log10(lux + 1)), so a change from 5 to 10
 * lux weighs like one from 200 to 400 - which is how the eye sees it.
 *
 * Spikes: a sample more than [jumpFactor] away from the previous raw sample is held as a suspect
 * and not averaged. If the next sample confirms it (close to the suspect) both go in; if the next
 * one is back near the old level the suspect is dropped. A single flash, a hand over the sensor
 * or a remote's IR blip is therefore ignored. NaN/negative/infinite samples are ignored.
 *
 * A sample after more than [staleMs] of silence (or with the clock gone backwards) restarts the
 * average from that sample. Deterministic for a given [clock]. Not thread-safe (main thread).
 *
 * @param alpha EMA weight of each new sample, 0 < alpha <= 1.
 */
class LuxSmoother(
    private val alpha: Float,
    private val clock: () -> Long,
    private val jumpFactor: Float = 4f,
    private val staleMs: Long = 5 * 60_000L
) {
    private var ema = Double.NaN
    private var lastRaw = Double.NaN
    private var suspect = Double.NaN
    private var lastMs = 0L
    private val a = alpha.toDouble().coerceIn(0.01, 1.0)
    private val jump = log10(jumpFactor.toDouble().coerceAtLeast(1.01))

    /** Current smoothed lux, or null before the first valid sample. */
    fun value(): Float? = if (ema.isNaN()) null else fromLog(ema)

    fun reset() {
        ema = Double.NaN; lastRaw = Double.NaN; suspect = Double.NaN
    }

    /** Feeds one sample and returns the smoothed lux (or the sample itself if it is the first). */
    fun add(lux: Float): Float {
        val now = clock()
        if (!lux.isFinite() || lux < 0f) return value() ?: 0f
        val x = log10(lux.toDouble() + 1.0)
        if (ema.isNaN() || now < lastMs || now - lastMs > staleMs) {
            ema = x; lastRaw = x; suspect = Double.NaN; lastMs = now
            return fromLog(ema)
        }
        lastMs = now
        if (!suspect.isNaN()) {
            if (abs(x - suspect) <= jump) {
                feed(suspect); feed(x)
                suspect = Double.NaN
                return fromLog(ema)
            }
            suspect = Double.NaN
            if (abs(x - lastRaw) > jump) {
                suspect = x
                return fromLog(ema)
            }
        } else if (abs(x - lastRaw) > jump) {
            suspect = x
            return fromLog(ema)
        }
        feed(x)
        return fromLog(ema)
    }

    private fun feed(x: Double) {
        ema += a * (x - ema)
        lastRaw = x
    }

    private fun fromLog(v: Double): Float = (10.0.pow(v) - 1.0).toFloat().coerceAtLeast(0f)
}
