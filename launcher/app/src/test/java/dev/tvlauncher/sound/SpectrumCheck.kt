package dev.tvlauncher.sound

/**
 * Test-side spectral check for "no HF fizz" on short sounds.
 * Hann-windowed Goertzel on a 10 Hz grid (finer than the ~20 Hz bins of a 50 ms tone, so narrow
 * spurs between grid points cannot hide). Reports total band power and the strongest bin.
 */
object SpectrumCheck {
    const val STEP_HZ = 10
    const val LIMIT_DB = -20.0

    class Result(val bandPowerDb: Double, val peakBinDb: Double) {
        val ok get() = bandPowerDb <= LIMIT_DB && peakBinDb <= LIMIT_DB
        override fun toString() = "band=%.1f dB peakbin=%.1f dB".format(bandPowerDb, peakBinDb)
    }

    private fun power(x: DoubleArray, rate: Int, f: Double): Double {
        val c = 2 * Math.cos(2 * Math.PI * f / rate)
        var s1 = 0.0; var s2 = 0.0
        for (v in x) { val s0 = v + c * s1 - s2; s2 = s1; s1 = s0 }
        return s1 * s1 + s2 * s2 - c * s1 * s2
    }

    /** Band power in [lo, hi] relative to total power (0..Nyquist), and strongest in-band bin vs strongest bin. */
    fun check(pcm: ShortArray, rate: Int, lo: Int = 6000, hi: Int = 10000): Result {
        val n = pcm.size
        val x = DoubleArray(n) { pcm[it] * (0.5 - 0.5 * Math.cos(2 * Math.PI * it / (n - 1))) }
        var total = 0.0; var band = 0.0; var peak = 0.0; var bandPeak = 0.0
        var f = STEP_HZ
        while (f < rate / 2) {
            val p = power(x, rate, f.toDouble())
            total += p
            if (p > peak) peak = p
            if (f in lo..hi) { band += p; if (p > bandPeak) bandPeak = p }
            f += STEP_HZ
        }
        fun db(r: Double) = 10 * Math.log10(maxOf(r, 1e-30))
        return Result(db(band / total), db(bandPeak / peak))
    }
}
