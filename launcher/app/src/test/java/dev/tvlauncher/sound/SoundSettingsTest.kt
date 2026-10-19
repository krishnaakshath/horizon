package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test

class SoundSettingsTest {
    @Test fun defaults() {
        val d = SoundSettings()
        assertTrue(d.enabled); assertEquals("glass", d.themeId); assertEquals(60, d.volume)
        assertFalse(d.useCustomFolder)
        assertTrue(SoundEvent.values().all { d.perEvent[it] == true })
    }

    @Test fun round_trip() {
        val s = SoundSettings(false, "retro", 30, SoundEvent.values().associateWith { it != SoundEvent.MOVE }, true)
        assertEquals(s, SoundSettings.fromJson(s.toJson()))
    }

    @Test fun corrupt_and_blank_give_defaults() {
        assertEquals(SoundSettings(), SoundSettings.fromJson(""))
        assertEquals(SoundSettings(), SoundSettings.fromJson("{not json"))
        assertEquals(SoundSettings(), SoundSettings.fromJson("[1,2]"))
    }

    @Test fun unknown_fields_ignored_and_partial_filled() {
        val s = SoundSettings.fromJson("""{"themeId":"soft","future":{"a":1},"perEvent":{"MOVE":false,"BOGUS":false}}""")
        assertEquals("soft", s.themeId)
        assertEquals(false, s.perEvent[SoundEvent.MOVE])
        assertEquals(true, s.perEvent[SoundEvent.SELECT])
        assertEquals(60, s.volume)
    }

    @Test fun volume_clamped() {
        assertEquals(100, SoundSettings.fromJson("""{"volume":900}""").volume)
        assertEquals(0, SoundSettings.fromJson("""{"volume":-5}""").volume)
    }

    @Test fun wrong_types_do_not_throw() {
        val s = SoundSettings.fromJson("""{"perEvent":5,"volume":"x","themeId":null}""")
        assertEquals(60, s.volume)
    }
}
