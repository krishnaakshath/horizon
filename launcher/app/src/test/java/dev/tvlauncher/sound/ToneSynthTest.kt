package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {
    private fun spec(f: Float = 440f, fe: Float = f, ms: Int = 100, gain: Float = 0.5f,
                     wave: Wave = Wave.SINE, h: List<Pair<Int, Float>> = emptyList()) =
        ToneSpec(f, fe, ms, wave, attackMs = 3, decayMs = 60, gain = gain, harmonics = h)

    private fun crossings(a: ShortArray): Int {
        var c = 0
        for (i in 1 until a.size) if ((a[i - 1] < 0) != (a[i] < 0)) c++
        return c
    }

    @Test fun length_matches_rate_times_duration() {
        assertEquals(2205, ToneSynth.render(spec(ms = 100)).size)
        assertEquals(4800, ToneSynth.render(spec(ms = 100), 48000).size)
    }

    @Test fun peak_never_exceeds_gain_bound() {
        for (w in Wave.values()) {
            val s = spec(gain = 0.4f, wave = w, h = listOf(2 to 0.5f, 3 to 0.3f))
            val peak = ToneSynth.render(s).maxOf { abs(it.toInt()) }
            assertTrue("$w peak $peak", peak <= 0.4f * Short.MAX_VALUE)
            assertTrue("$w audible", peak > 1000)
        }
    }

    @Test fun starts_and_ends_near_zero() {
        for (w in Wave.values()) {
            val pcm = ToneSynth.render(spec(wave = w, ms = 80))
            assertTrue(abs(pcm.first().toInt()) <= 50)
            assertTrue(abs(pcm.last().toInt()) <= 50)
        }
    }

    @Test fun deterministic() {
        val s = spec(300f, 900f, h = listOf(2 to 0.4f))
        assertArrayEquals(ToneSynth.render(s), ToneSynth.render(s))
    }

    @Test fun glide_changes_zero_crossing_density() {
        val flat = crossings(ToneSynth.render(spec(400f, 400f, ms = 200)))
        val up = crossings(ToneSynth.render(spec(400f, 1600f, ms = 200)))
        assertTrue("flat=$flat up=$up", up > flat * 1.5)
    }

    @Test fun zero_duration_is_empty() {
        assertEquals(0, ToneSynth.render(spec(ms = 0)).size)
    }
}
