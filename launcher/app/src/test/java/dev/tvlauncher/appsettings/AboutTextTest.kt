package dev.tvlauncher.appsettings

import dev.tvlauncher.device.EffectsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutTextTest {
    @Test
    fun ram_megabytesBelowOneGigabyte_elseOneDecimalGigabytes() {
        assertEquals("939 MB", AboutText.ram(962_000))
        assertEquals("1.5 GB", AboutText.ram(1_572_864))
        assertEquals("Unknown", AboutText.ram(0))
    }

    @Test
    fun effects_namesDeviceAndUserLevel() {
        assertEquals("Lite (low-RAM TV)", AboutText.effects(EffectsMode.LITE))
        assertEquals("Full", AboutText.effects(EffectsMode.FULL))
        assertEquals("Off", AboutText.motion(null))
        assertEquals("Lite", AboutText.motion(EffectsMode.LITE))
    }

    @Test
    fun entryAnimation_staysWithinBudget() {
        for (m in listOf(null, EffectsMode.LITE, EffectsMode.FULL)) {
            val s = SettingsStyle(0, m)
            assertTrue(s.entryMs + s.entryStaggerMs <= 250L)
        }
        assertFalse(SettingsStyle(0, null).animate)
    }

    @Test
    fun moduleProbe_detectsPresentAndMissingClasses() {
        assertTrue(ModuleProbe.isPresent("java.lang.String"))
        assertFalse(ModuleProbe.isPresent("dev.tvlauncher.ui.DefinitelyMissingPanel"))
    }
}
