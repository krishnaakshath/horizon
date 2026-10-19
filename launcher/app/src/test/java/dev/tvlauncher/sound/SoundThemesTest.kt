package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Before
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

    @Before fun resetShared() { SoundThemes.resetCalibrationForTest() }

    @Test fun all_and_byId_never_render() = synchronized(ToneSynth.renderCount) {
        val before = ToneSynth.renderCount.get()
        SoundThemes.all.forEach { SoundThemes.byId(it.id).events.size }
        SoundThemes.byId("nope")
        assertEquals(before, ToneSynth.renderCount.get())
    }

    @Test fun calibratedGain_computedOnce_andCached() = synchronized(ToneSynth.renderCount) {
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

    private fun tone(ms: Int, vararg partials: Pair<Double, Double>): ShortArray {
        val n = 22050 * ms / 1000
        return ShortArray(n) { i ->
            val t = i / 22050.0
            val fade = minOf(1.0, i / 44.0, (n - 1 - i) / 44.0)
            (partials.sumOf { (f, a) -> a * Math.sin(2 * Math.PI * f * t) } * fade * 8000).toInt().toShort()
        }
    }

    @Test fun highBandCheck_failsFor_6150Hzspur_at_minus10dB() {
        val r = SpectrumCheck.check(tone(50, 1568.0 to 1.0, 6150.0 to 0.3), 22050)
        assertFalse(r.toString(), r.ok)
    }

    @Test fun highBandCheck_failsFor_7050Hzspur() {
        val r = SpectrumCheck.check(tone(50, 1568.0 to 1.0, 7050.0 to 0.3), 22050)
        assertFalse(r.toString(), r.ok)
    }

    @Test fun highBandCheck_failsFor_8kHzTone() {
        val r = SpectrumCheck.check(tone(50, 8000.0 to 1.0), 22050)
        assertFalse(r.toString(), r.ok)
    }

    @Test fun highBandCheck_passesFor_pureLowTone() {
        val r = SpectrumCheck.check(tone(50, 1568.0 to 1.0), 22050)
        assertTrue(r.toString(), r.ok)
    }

    @Test fun move_has_no_energy_above_6k() {
        for (t in SoundThemes.all) {
            val sp = SoundThemes.spec(t.id, SoundEvent.MOVE)
            if (sp.gain <= 0f) continue
            val r = SpectrumCheck.check(ToneSynth.render(sp, 22050), 22050)
            println("MOVEDB ${t.id} $r")
            assertTrue("${t.id} $r", r.ok)
        }
    }
}
