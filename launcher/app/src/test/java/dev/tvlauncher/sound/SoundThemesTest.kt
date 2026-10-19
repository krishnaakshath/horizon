package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test

class SoundThemesTest {
    @Test fun eight_themes_with_expected_ids() {
        assertEquals(listOf("soft", "crisp", "glass", "retro", "aurora", "pulse", "minimal", "bell"),
            SoundThemes.all.map { it.id })
    }

    @Test fun every_theme_defines_every_event() {
        for (t in SoundThemes.all) for (e in SoundEvent.values())
            assertNotNull("${t.id}/$e", t.events[e])
    }

    @Test fun durations_within_limits() {
        for (t in SoundThemes.all) {
            for ((e, s) in t.events) assertTrue("${t.id}/$e ${s.durationMs}", s.durationMs < 450)
            assertTrue("${t.id} MOVE", t.events.getValue(SoundEvent.MOVE).durationMs < 60)
        }
    }

    @Test fun theme_pcm_under_200kb() {
        for (t in SoundThemes.all) {
            val bytes = SoundEvent.values().sumOf { ToneSynth.render(SoundThemes.spec(t.id, it)).size * 2 }
            assertTrue("${t.id} $bytes", bytes < 200 * 1024)
        }
    }

    @Test fun byId_falls_back_to_glass() {
        assertEquals("retro", SoundThemes.byId("retro").id)
        assertEquals("glass", SoundThemes.byId("nope").id)
    }

    private fun rms(a: ShortArray) = Math.sqrt(a.sumOf { it.toDouble() * it } / a.size) / Short.MAX_VALUE

    @Test fun rms_within_2dB_of_target_for_audible_events() {
        for (t in SoundThemes.all) for (e in SoundEvent.values()) {
            val s = SoundThemes.spec(t.id, e)
            if (s.gain <= 0f) continue
            val r = rms(ToneSynth.render(s))
            val db = 20 * Math.log10(r / SoundThemes.targetRms(e))
            assertTrue("${t.id}/$e ${"%.2f".format(db)} dB", Math.abs(db) <= 2.0)
        }
    }

    @Test fun minimal_only_select_back_launch_audible() {
        val audible = SoundThemes.byId("minimal").events.filter { it.value.gain > 0f }.keys
        assertEquals(setOf(SoundEvent.SELECT, SoundEvent.BACK, SoundEvent.LAUNCH), audible)
    }

    @Test fun move_is_soft_attack_and_has_no_hf_fizz() {
        for (t in SoundThemes.all) {
            val m = t.events.getValue(SoundEvent.MOVE)
            if (m.gain <= 0f) continue
            assertTrue("${t.id} attack", m.attackMs >= 4)
            val maxF = maxOf(m.freqHz, m.freqEndHz)
            val top = (m.harmonics.maxOfOrNull { it.first } ?: 1)
            assertTrue("${t.id} hf", maxF * top <= 6000f)
            assertNull("${t.id} MOVE has no tail", m.tail)
        }
    }

    @Test fun launch_has_two_notes_ascending_error_descending() {
        for (t in SoundThemes.all) {
            val l = t.events.getValue(SoundEvent.LAUNCH); val er = t.events.getValue(SoundEvent.ERROR)
            if (l.gain > 0f) { assertNotNull(t.id, l.second); assertTrue(t.id, l.second!!.freqHz > l.freqHz) }
            if (er.gain > 0f) { assertNotNull(t.id, er.second); assertTrue(t.id, er.second!!.freqHz < er.freqHz) }
        }
    }

    @Test fun all_and_byId_never_render() {
        val before = ToneSynth.renderCount.get()
        SoundThemes.all.forEach { SoundThemes.byId(it.id).events.size }
        SoundThemes.byId("nope")
        assertEquals(before, ToneSynth.renderCount.get())
    }

    @Test fun calibratedGain_computedOnce_andCached() {
        SoundThemes.resetCalibrationForTest()
        val c0 = ToneSynth.renderCount.get()
        val g1 = SoundThemes.spec("glass", SoundEvent.SELECT).gain
        val c1 = ToneSynth.renderCount.get()
        assertTrue(c1 > c0)
        val g2 = SoundThemes.spec("glass", SoundEvent.SELECT).gain
        assertEquals(c1, ToneSynth.renderCount.get())
        assertEquals(g1, g2, 0f)
    }

    @Test fun tails_withinRuleset() {
        for (t in SoundThemes.all) for ((e, sp) in t.events) {
            val taps = sp.tail?.taps ?: continue
            val id = "${t.id}/$e"
            assertTrue("$id count", taps.size in 1..3)
            assertTrue("$id delays", taps.all { it.first in 35..220 })
            assertTrue("$id first gain", taps.first().second <= 0.4f)
            assertTrue("$id monotonic", taps.zipWithNext().all { (a, b) -> b.second <= a.second })
        }
    }

    private fun goertzel(x: ShortArray, rate: Int, f: Double): Double {
        val n = x.size
        val c = 2 * Math.cos(2 * Math.PI * f / rate)
        var s1 = 0.0; var s2 = 0.0
        for (i in 0 until n) {
            val hann = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1))
            val s0 = x[i] * hann + c * s1 - s2
            s2 = s1; s1 = s0
        }
        return Math.sqrt(s1 * s1 + s2 * s2 - c * s1 * s2)
    }

    @Test fun move_has_no_energy_above_6k() {
        for (t in SoundThemes.all) {
            val sp = SoundThemes.spec(t.id, SoundEvent.MOVE)
            if (sp.gain <= 0f) continue
            val pcm = ToneSynth.render(sp, 22050)
            val peak = (100..5900 step 50).maxOf { goertzel(pcm, 22050, it.toDouble()) }
            val high = (6000..10000 step 100).maxOf { goertzel(pcm, 22050, it.toDouble()) }
            val db = 20 * Math.log10(high / peak)
            assertTrue("${t.id} high band ${"%.1f".format(db)} dB", db <= -20.0)
        }
    }
}
