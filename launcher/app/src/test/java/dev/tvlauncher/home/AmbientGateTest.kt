package dev.tvlauncher.home

import dev.tvlauncher.ambient.WallpaperSettings
import dev.tvlauncher.ui.BackdropLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientGateTest {
    private fun gate(
        resumed: Boolean = true, overlay: Boolean = false, spotlight: Boolean = false,
        windowFocus: Boolean = true, safeMode: Boolean = false, splash: Boolean = false
    ) = AmbientGate.shouldStart(resumed, overlay, spotlight, windowFocus, safeMode, splash)

    @Test fun idleHome_startsAmbient() = assertTrue(gate())

    @Test fun neverWhenBlocked() {
        assertFalse(gate(resumed = false))
        assertFalse(gate(overlay = true))
        assertFalse(gate(spotlight = true))
        assertFalse(gate(windowFocus = false)) // a TileMenu dialog or system dialog is up
        assertFalse(gate(safeMode = true))
        assertFalse(gate(splash = true))
    }

    @Test fun idleMinutes_comeFromAmbientSettings_default10() {
        assertEquals(10, WallpaperSettings().idleMinutes)
        assertEquals(10 * 60_000L, AmbientGate.timeoutMs(WallpaperSettings()))
        assertEquals(60_000L, AmbientGate.timeoutMs(WallpaperSettings(idleMinutes = 1)))
    }

    @Test fun clockFollowsAppearance() {
        val s = WallpaperSettings(clock24h = false)
        assertTrue(AmbientGate.withClock(s, is24h = true).clock24h)
        assertFalse(AmbientGate.withClock(s.copy(clock24h = true), is24h = false).clock24h)
        assertTrue(AmbientGate.withClock(s, is24h = false) === s) // unchanged: no save needed
    }

    @Test fun backdropGlow_is55PercentOverAWallpaper() {
        assertEquals(255, BackdropLayers.glowAlpha(hasBaseLayer = false))
        assertEquals(140, BackdropLayers.glowAlpha(hasBaseLayer = true))
    }
}
