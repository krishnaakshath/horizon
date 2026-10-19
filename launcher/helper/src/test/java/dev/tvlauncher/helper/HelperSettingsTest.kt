package dev.tvlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HelperSettingsTest {
    @Test fun defaults_are_off() {
        val d = HelperSettings()
        assertFalse(d.doubleBackEnabled)
        assertEquals(0, d.shortcutKey)
        assertEquals(500L, d.windowMs)
        assertEquals(ExclusionList.DEFAULTS, d.exclusions)
    }

    @Test fun round_trip() {
        val s = HelperSettings(true, 700, 82, setOf("a.b", "c.d"))
        assertEquals(s, HelperSettings.fromJson(s.toJson()))
    }

    @Test fun tolerant_of_garbage() {
        assertEquals(HelperSettings(), HelperSettings.fromJson("not json"))
        assertEquals(HelperSettings(), HelperSettings.fromJson(null))
        assertEquals(HelperSettings(), HelperSettings.fromJson("{}"))
    }

    @Test fun tolerant_of_wrong_types_and_clamps() {
        val s = HelperSettings.fromJson("""{"doubleBack":"yes","windowMs":99999,"shortcutKey":"x","exclusions":5}""")
        assertFalse(s.doubleBackEnabled)
        assertEquals(900L, s.windowMs)
        assertEquals(0, s.shortcutKey)
        assertEquals(ExclusionList.DEFAULTS, s.exclusions)
    }

    @Test fun disallowed_shortcut_is_dropped_on_load() {
        val s = HelperSettings.fromJson("""{"shortcutKey":4}""")
        assertEquals(0, s.shortcutKey)
    }
}
