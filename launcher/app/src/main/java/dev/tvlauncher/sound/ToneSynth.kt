package dev.tvlauncher.sound

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class Wave { SINE, TRIANGLE, SQUARE_SOFT }

/**
 * Parametric description of one short UI tone.
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
    val harmonics: List<Pair<Int, Float>> = emptyList()
)

/** Pure-Kotlin PCM synthesizer (mono, 16-bit). Deterministic. */
object ToneSynth {
    private const val EDGE_FADE_MS = 2.0

    private fun shape(wave: Wave, phase: Double): Double = when (wave) {
        Wave.SINE -> sin(phase)
        Wave.TRIANGLE -> 2.0 / PI * asin(sin(phase))
        Wave.SQUARE_SOFT -> tanh(3.0 * sin(phase)) / tanh(3.0)
    }

    fun render(spec: ToneSpec, sampleRate: Int = 22050): ShortArray {
        val n = (sampleRate.toLong() * spec.durationMs / 1000).toInt()
        if (n <= 0) return ShortArray(0)
        val out = ShortArray(n)
        val attackN = (sampleRate * spec.attackMs / 1000.0).coerceAtLeast(1.0)
        val decayS = (spec.decayMs / 1000.0).coerceAtLeast(0.001)
        val fadeN = (sampleRate * EDGE_FADE_MS / 1000.0).coerceAtMost(n / 2.0).coerceAtLeast(1.0)
        val norm = 1.0 + spec.harmonics.sumOf { kotlin.math.abs(it.second.toDouble()) }
        val peak = spec.gain.coerceIn(0f, 1f) * Short.MAX_VALUE
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val frac = if (n > 1) i.toDouble() / (n - 1) else 0.0
            val f = spec.freqHz + (spec.freqEndHz - spec.freqHz) * frac
            var v = shape(spec.wave, phase)
            for ((mult, g) in spec.harmonics) v += g * shape(spec.wave, phase * mult)
            v /= norm
            phase += 2.0 * PI * f / sampleRate
            val att = if (i < attackN) i / attackN else 1.0
            val tAfter = (t - spec.attackMs / 1000.0).coerceAtLeast(0.0)
            val dec = exp(-4.6 * tAfter / decayS)
            val fadeIn = if (i < fadeN) i / fadeN else 1.0
            val fadeOut = if (n - 1 - i < fadeN) (n - 1 - i) / fadeN else 1.0
            val s = v * att * dec * fadeIn * fadeOut * peak
            out[i] = s.toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
