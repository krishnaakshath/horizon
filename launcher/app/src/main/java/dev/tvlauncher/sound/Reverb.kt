package dev.tvlauncher.sound

/** Short echo/reverb tail: multi-tap delay. [taps] are (delayMs, gain) pairs. */
data class TailSpec(val taps: List<Pair<Int, Float>>)

object Reverb {
    /** Returns a new buffer of the same length with delayed, scaled copies of [x] mixed in. */
    fun apply(x: DoubleArray, sampleRate: Int, tail: TailSpec): DoubleArray {
        val out = x.copyOf()
        for ((ms, g) in tail.taps) {
            val d = (sampleRate.toLong() * ms / 1000).toInt()
            if (d <= 0) continue
            for (i in d until x.size) out[i] += g * x[i - d]
        }
        return out
    }
}
