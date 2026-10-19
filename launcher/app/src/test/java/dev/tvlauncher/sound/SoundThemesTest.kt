package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test

class SoundThemesTest {
    @Test fun four_themes_with_expected_ids() {
        assertEquals(listOf("soft", "crisp", "glass", "retro"), SoundThemes.all.map { it.id })
    }

    @Test fun every_theme_defines_every_event() {
        for (t in SoundThemes.all) for (e in SoundEvent.values())
            assertNotNull("${t.id}/$e", t.events[e])
    }

    @Test fun durations_within_limits() {
        for (t in SoundThemes.all) {
            for ((e, s) in t.events) assertTrue("${t.id}/$e ${s.durationMs}", s.durationMs < 400)
            assertTrue("${t.id} MOVE", t.events.getValue(SoundEvent.MOVE).durationMs < 60)
        }
    }

    @Test fun theme_pcm_under_200kb() {
        for (t in SoundThemes.all) {
            val bytes = t.events.values.sumOf { ToneSynth.render(it).size * 2 }
            assertTrue("${t.id} $bytes", bytes < 200 * 1024)
        }
    }

    @Test fun byId_falls_back_to_glass() {
        assertEquals("retro", SoundThemes.byId("retro").id)
        assertEquals("glass", SoundThemes.byId("nope").id)
    }
}
