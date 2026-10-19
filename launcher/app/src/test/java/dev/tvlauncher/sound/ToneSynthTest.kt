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

    @Test fun length_within_one_sample_for_many_durations() {
        for (rate in listOf(22050, 44100)) for (ms in listOf(1, 33, 45, 123, 399)) {
            val n = ToneSynth.render(spec(ms = ms), rate).size
            assertTrue("$rate/$ms n=$n", abs(n - rate * ms / 1000) <= 1)
        }
    }

    @Test fun zero_duration_is_empty() {
        assertEquals(0, ToneSynth.render(spec(ms = 0)).size)
    }

    private fun rms(a: ShortArray, from: Int, to: Int) =
        Math.sqrt((from until to).sumOf { a[it].toDouble() * a[it] } / (to - from))

    @Test fun tail_keeps_peak_bound_and_ends_at_zero() {
        val s = spec(gain = 0.5f, ms = 400, h = listOf(2 to 0.5f))
            .copy(tail = TailSpec(listOf(40 to 0.35f, 80 to 0.22f, 120 to 0.12f)))
        val pcm = ToneSynth.render(s)
        assertTrue(pcm.maxOf { abs(it.toInt()) } <= 0.5f * Short.MAX_VALUE)
        assertTrue(abs(pcm.last().toInt()) <= 50)
    }

    @Test fun tail_adds_energy_after_dry_decay() {
        val dry = spec(ms = 300).copy(decayMs = 20)
        val wet = dry.copy(tail = TailSpec(listOf(80 to 0.35f)))
        val a = ToneSynth.render(dry); val b = ToneSynth.render(wet)
        val from = 22050 * 100 / 1000; val to = 22050 * 200 / 1000
        assertTrue(rms(b, from, to) > rms(a, from, to) * 2)
    }

    @Test fun fm_peak_bound_deterministic_and_decays() {
        val s = spec(1200f, gain = 0.45f, ms = 300).copy(decayMs = 200, fm = FmSpec(3.5f, 2f, 80))
        val a = ToneSynth.render(s)
        assertArrayEquals(a, ToneSynth.render(s))
        assertTrue(a.maxOf { abs(it.toInt()) } <= 0.45f * Short.MAX_VALUE)
        val n = a.size
        assertTrue(rms(a, 100, n / 3) > rms(a, 2 * n / 3, n - 1))
    }

    @Test fun fm_changes_spectrum_vs_plain() {
        val plain = spec(1000f, ms = 150)
        val fm = plain.copy(fm = FmSpec(3.5f, 3f, 100))
        assertFalse(ToneSynth.render(plain).contentEquals(ToneSynth.render(fm)))
    }

    @Test fun second_note_adds_late_energy_and_changes_pitch() {
        val one = spec(500f, ms = 300).copy(decayMs = 30)
        val two = one.copy(second = SecondNote(150, 1000f))
        val a = ToneSynth.render(one); val b = ToneSynth.render(two)
        assertTrue(rms(b, 4000, 6000) > rms(a, 4000, 6000) * 3)
        assertTrue(abs(b.last().toInt()) <= 50)
    }

    @Test fun heavy_stack_never_clips() {
        val s = spec(800f, gain = 0.95f, ms = 440, wave = Wave.SQUARE_SOFT, h = listOf(2 to 1f, 3 to 1f))
            .copy(second = SecondNote(50, 900f), tail = TailSpec(listOf(30 to 0.9f, 60 to 0.9f, 90 to 0.9f)))
        val peak = ToneSynth.render(s).maxOf { abs(it.toInt()) }
        assertTrue(peak <= 0.95f * Short.MAX_VALUE)
    }
}
