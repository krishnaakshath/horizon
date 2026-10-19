package dev.tvlauncher.sound

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class Wave { SINE, TRIANGLE, SQUARE_SOFT }

/** FM / bell synthesis: modulator frequency = carrier * [ratio]; index decays with [indexDecayMs]. */
data class FmSpec(val ratio: Float, val index: Float, val indexDecayMs: Int)

/** A second voice starting [atMs] into the tone (used for two-note motifs). */
data class SecondNote(val atMs: Int, val freqHz: Float, val freqEndHz: Float = freqHz)

/**
 * Parametric description of one short UI tone. [durationMs] is the total rendered length,
 * including any [tail]. [gain] is the hard peak bound (0 = silent).
 * [harmonics] are (integer multiple of the fundamental, relative gain) pairs.
 */
data class ToneSpec(
    val freqHz: Float,
    val freqEndHz: Float = freqHz,
    val durationMs: Int,
    val wave: Wave,
    val attackMs: Int,
    val decayMs: Int,
    val gain: Float,
    val harmonics: List<Pair<Int, Float>> = emptyList(),
    val fm: FmSpec? = null,
    val tail: TailSpec? = null,
    val second: SecondNote? = null
)

/** Pure-Kotlin PCM synthesizer (mono, 16-bit). Deterministic. */
object ToneSynth {
    private const val EDGE_FADE_MS = 2.0

    /** Number of render() calls (lets tests prove that metadata access never renders). */
    val renderCount = java.util.concurrent.atomic.AtomicInteger()

    private fun shape(wave: Wave, phase: Double): Double = when (wave) {
        Wave.SINE -> sin(phase)
        Wave.TRIANGLE -> 2.0 / PI * asin(sin(phase))
        Wave.SQUARE_SOFT -> tanh(3.0 * sin(phase)) / tanh(3.0)
    }

    private fun voice(y: DoubleArray, start: Int, f0: Float, f1: Float, spec: ToneSpec, rate: Int) {
        val len = y.size - start
        if (len <= 0) return
        val attackN = (rate * spec.attackMs / 1000.0).coerceAtLeast(1.0)
        val decayS = (spec.decayMs / 1000.0).coerceAtLeast(0.001)
        val norm = 1.0 + spec.harmonics.sumOf { kotlin.math.abs(it.second.toDouble()) }
        val fm = spec.fm
        var phase = 0.0
        for (k in 0 until len) {
            val t = k.toDouble() / rate
            val frac = if (len > 1) k.toDouble() / (len - 1) else 0.0
            val f = f0 + (f1 - f0) * frac
            var carrierPhase = phase
            if (fm != null) {
                val idx = fm.index * exp(-4.6 * t / (fm.indexDecayMs / 1000.0).coerceAtLeast(0.001))
                carrierPhase += idx * sin(phase * fm.ratio)
            }
            var v = shape(spec.wave, carrierPhase)
            for ((mult, g) in spec.harmonics) v += g * shape(spec.wave, phase * mult)
            v /= norm
            phase += 2.0 * PI * f / rate
            val att = if (k < attackN) k / attackN else 1.0
            val tAfter = (t - spec.attackMs / 1000.0).coerceAtLeast(0.0)
            y[start + k] += v * att * exp(-4.6 * tAfter / decayS)
        }
    }

    fun render(spec: ToneSpec, sampleRate: Int = 22050): ShortArray {
        renderCount.incrementAndGet()
        val n = (sampleRate.toLong() * spec.durationMs / 1000).toInt()
        if (n <= 0) return ShortArray(0)
        if (spec.gain <= 0f) return ShortArray(n)
        var y = DoubleArray(n)
        voice(y, 0, spec.freqHz, spec.freqEndHz, spec, sampleRate)
        spec.second?.let {
            voice(y, (sampleRate.toLong() * it.atMs / 1000).toInt(), it.freqHz, it.freqEndHz, spec, sampleRate)
        }
        spec.tail?.let { y = Reverb.apply(y, sampleRate, it) }
        val fadeN = (sampleRate * EDGE_FADE_MS / 1000.0).coerceAtMost(n / 2.0).coerceAtLeast(1.0)
        val peak = spec.gain.coerceIn(0f, 1f) * Short.MAX_VALUE
        val out = ShortArray(n)
        for (i in 0 until n) {
            val fadeIn = if (i < fadeN) i / fadeN else 1.0
            val fadeOut = if (n - 1 - i < fadeN) (n - 1 - i) / fadeN else 1.0
            // tanh is a soft limiter: |out| < peak always, so stacked voices/tails never clip.
            out[i] = (tanh(y[i] * fadeIn * fadeOut) * peak).toInt().toShort()
        }
        return out
    }
}
