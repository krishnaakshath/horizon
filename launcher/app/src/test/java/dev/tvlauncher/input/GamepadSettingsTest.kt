package dev.tvlauncher.input

import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadSettingsTest {
    @Test fun defaults() {
        val d = GamepadSettings()
        assertEquals(false, d.swapAB)
        assertEquals(GamepadSettings.NORMAL, d.sensitivity)
        assertEquals(true, d.guideHome)
        assertEquals(false, d.vibrate)
        assertEquals("swap=0;sens=1;guide=1;vib=0", d.encode())
    }

    @Test fun roundTrip() {
        val all = listOf(
            GamepadSettings(swapAB = true, sensitivity = GamepadSettings.HIGH, guideHome = false, vibrate = true),
            GamepadSettings(sensitivity = GamepadSettings.LOW),
        )
        for (s in all) assertEquals(s, GamepadSettings.decode(s.encode()))
    }

    @Test fun brokenText_fallsBackPerField() {
        assertEquals(GamepadSettings(), GamepadSettings.decode(null))
        assertEquals(GamepadSettings(), GamepadSettings.decode("garbage"))
        assertEquals(GamepadSettings(swapAB = true), GamepadSettings.decode("swap=1;sens=9;guide=maybe;;=;vib"))
        assertEquals(GamepadSettings(guideHome = false), GamepadSettings.decode(" guide = 0 "))
    }

    @Test fun labels_coverEveryLevel() {
        assertEquals(3, GamepadSettings.SENSITIVITY_LABELS.size)
        assertEquals("Normal", GamepadSettings.SENSITIVITY_LABELS[GamepadSettings.NORMAL])
    }
}
