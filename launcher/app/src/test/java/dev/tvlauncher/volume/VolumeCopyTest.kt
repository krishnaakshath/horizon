package dev.tvlauncher.volume

import dev.tvlauncher.appsettings.SectionIds
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.volume.core.BarMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeCopyTest {
    @Test fun output_labels() {
        assertEquals("TV speakers", VolumeCopy.outputLabel(2))
        assertEquals("HDMI ARC sound system", VolumeCopy.outputLabel(10))
        assertEquals("Bluetooth speaker", VolumeCopy.outputLabel(8))
        assertEquals("Optical (S/PDIF)", VolumeCopy.outputLabel(6))
        assertNull(VolumeCopy.outputLabel(18)) // telephony
        assertNull(VolumeCopy.outputLabel(21)) // bus
    }

    @Test fun outputs_are_distinct_and_speakers_first() {
        assertEquals(listOf("TV speakers", "HDMI ARC sound system", "Headphones"), VolumeCopy.outputs(listOf(4, 10, 2, 24, 3, 21)))
        assertEquals(emptyList<String>(), VolumeCopy.outputs(emptyList()))
    }

    @Test fun playing_on() {
        assertEquals("JBL Flip", PlayingOn.label("JBL Flip" to false, listOf("TV speakers", "Bluetooth speaker")))
        assertEquals("TV speakers", PlayingOn.label("TV" to true, listOf("TV speakers")))
        assertEquals("TV speakers or HDMI ARC sound system", PlayingOn.label("TV" to true, listOf("TV speakers", "HDMI ARC sound system")))
        assertEquals("TV speakers", PlayingOn.label(null, emptyList()))
        assertEquals("Headphones", PlayingOn.label(null, listOf("Headphones")))
    }

    @Test fun bar_motion_follows_the_launcher() {
        assertEquals(BarMotion.OFF, VolumeCopy.barMotion(null, 1f))
        assertEquals(BarMotion.OFF, VolumeCopy.barMotion(EffectsMode.FULL, 0f))
        assertEquals(BarMotion.LITE, VolumeCopy.barMotion(EffectsMode.LITE, 1f))
        assertEquals(BarMotion.FULL, VolumeCopy.barMotion(EffectsMode.FULL, 1f))
        assertEquals("lite", VolumeCopy.motionWord(BarMotion.LITE))
    }

    @Test fun bar_subtitles_are_calm_and_explain_the_helper() {
        val s = VolumeCopy.HelperState.PENDING
        assertEquals("Off: the TV shows its own volume popup", VolumeCopy.barSubtitle(false, s))
        assertEquals("Shown when you change the volume", VolumeCopy.barSubtitle(true, s))
        assertTrue(VolumeCopy.barSubtitle(true, VolumeCopy.HelperState.NOT_INSTALLED).contains("Add ${Brand.HELPER}"))
        assertEquals("Shown in every app when you change the volume",
            VolumeCopy.barSubtitle(true, VolumeCopy.HelperState(true, true, true, true)))
        assertTrue(VolumeCopy.barSubtitle(true, VolumeCopy.HelperState(true, true, false, true)).contains("running again"))
        assertTrue(VolumeCopy.barSubtitle(true, VolumeCopy.HelperState.NOT_RUNNING).contains("Turn on ${Brand.HELPER}"))
        for (st in listOf(s, VolumeCopy.HelperState.NOT_INSTALLED, VolumeCopy.HelperState.NOT_RUNNING)) {
            val t = VolumeCopy.barSubtitle(true, st).lowercase()
            assertFalse(t, t.contains("adb") || t.contains("accessibility") || t.contains("service"))
        }
    }

    @Test fun level_label() {
        assertEquals("12", VolumeCopy.levelLabel(12, false))
        assertEquals("Muted", VolumeCopy.levelLabel(12, true))
    }

    @Test fun page_is_within_budget_and_in_the_sound_group_before_ui_sounds() {
        val spec = VolumeSection.spec
        assertTrue(spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
        assertEquals(VolumeSection.ID, spec.id)
        assertTrue((spec.primaryRows + spec.advancedRows).all { it.startsWith("sound_output.") })
        assertEquals(SettingsGroup.SOUND, VolumeSection.group)
        val r = SettingsRegistry.newDefault()
        VolumeSection.register(r)
        VolumeSection.register(r) // idempotent
        val ids = r.sections().map { it.id }
        assertEquals(ids.indexOf(SectionIds.SOUNDS) - 1, ids.indexOf(VolumeSection.ID))
        assertEquals(1, ids.count { it == VolumeSection.ID })
    }

    @Test fun register_before_a_missing_id_appends() {
        val r = SettingsRegistry(emptyList())
        r.registerBefore(VolumeSection, "nope")
        assertEquals(listOf(VolumeSection.ID), r.sections().map { it.id })
    }
}
