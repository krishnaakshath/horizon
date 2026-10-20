package dev.tvlauncher.ambient

import dev.tvlauncher.ambient.AmbientSettingsRows.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AmbientSettingsRowsTest {
    private fun rows(s: WallpaperSettings, access: Boolean = false) =
        AmbientSettingsRows.rows(s, access).associateBy { it.key }

    @Test fun allKeysPresent_inStableOrder() {
        assertEquals(
            listOf("wallpaper", "slideshowInterval", "dim", "notificationsEnabled", "notificationAccess",
                "showNotificationText", "nightDim", "clock24h", "clockPosition", "idleMinutes", "pixelShift"),
            AmbientSettingsRows.rows(WallpaperSettings(), false).map { it.key }
        )
    }

    @Test fun kinds() {
        val r = rows(WallpaperSettings())
        assertEquals(Kind.ACTION, r.getValue("wallpaper").kind)
        assertEquals(Kind.STEPPER, r.getValue("dim").kind)
        assertEquals(Kind.TOGGLE, r.getValue("notificationsEnabled").kind)
        assertEquals(Kind.CARD, r.getValue("notificationAccess").kind)
        assertEquals(Kind.CHOICE, r.getValue("clockPosition").kind)
        assertEquals(Kind.TOGGLE, r.getValue("pixelShift").kind)
    }

    @Test fun notificationsOff_hidesTextRowAndCard_showsHint() {
        val r = rows(WallpaperSettings(notificationsEnabled = false))
        assertFalse(r.getValue("showNotificationText").visible)
        assertFalse(r.getValue("notificationAccess").visible)
        assertEquals("Turn on notification chips first", r.getValue("notificationsEnabled").hint)
    }

    @Test fun notificationsOn_accessMissing_showsCard() {
        val r = rows(WallpaperSettings(notificationsEnabled = true), access = false)
        assertTrue(r.getValue("notificationAccess").visible)
        assertTrue(r.getValue("showNotificationText").visible)
        assertNull(r.getValue("notificationsEnabled").hint)
        assertFalse(rows(WallpaperSettings(notificationsEnabled = true), access = true).getValue("notificationAccess").visible)
    }

    @Test fun slideshowInterval_onlyForSlideshow() {
        assertFalse(rows(WallpaperSettings()).getValue("slideshowInterval").visible)
        val r = rows(WallpaperSettings(source = WallpaperSource.Slideshow(File("/w"), 120)))
        assertTrue(r.getValue("slideshowInterval").visible)
        assertEquals("2 min", r.getValue("slideshowInterval").value)
    }

    @Test fun values_render() {
        val r = rows(WallpaperSettings(dimPercent = 30, clock24h = false, idleMinutes = 15, source = WallpaperSource.BuiltIn("nebula")))
        assertEquals("30%", r.getValue("dim").value)
        assertEquals("Off", r.getValue("clock24h").value)
        assertEquals("15 min", r.getValue("idleMinutes").value)
        assertEquals("Nebula", r.getValue("wallpaper").value)
        assertEquals("On", r.getValue("pixelShift").value)
    }

    @Test fun toggle_flipsBooleans_andOffClearsNothingElse() {
        var s = WallpaperSettings()
        s = AmbientSettingsRows.toggle(s, "notificationsEnabled")
        assertTrue(s.notificationsEnabled)
        s = AmbientSettingsRows.toggle(s, "showNotificationText")
        assertTrue(s.showNotificationText)
        s = AmbientSettingsRows.toggle(s, "pixelShift")
        assertFalse(s.pixelShift)
        s = AmbientSettingsRows.toggle(s, "clock24h")
        assertFalse(s.clock24h)
        assertEquals(s, AmbientSettingsRows.toggle(s, "dim")) // not a toggle: unchanged
    }

    @Test fun step_clampsAndCycles() {
        var s = WallpaperSettings(dimPercent = 50)
        s = AmbientSettingsRows.step(s, "dim", +1); assertEquals(60, s.dimPercent)
        s = AmbientSettingsRows.step(s, "dim", +1); assertEquals(60, s.dimPercent)
        s = AmbientSettingsRows.step(WallpaperSettings(idleMinutes = 10), "idleMinutes", +1); assertEquals(15, s.idleMinutes)
        s = AmbientSettingsRows.step(WallpaperSettings(idleMinutes = 1), "idleMinutes", -1); assertEquals(1, s.idleMinutes)
        s = AmbientSettingsRows.step(WallpaperSettings(), "clockPosition", +1); assertEquals(ClockPosition.CENTER, s.clockPosition)
        s = AmbientSettingsRows.step(WallpaperSettings(clockPosition = ClockPosition.TOP_LEFT), "clockPosition", +1)
        assertEquals(ClockPosition.BOTTOM_LEFT, s.clockPosition)
        val slide = WallpaperSettings(source = WallpaperSource.Slideshow(File("/w"), 60))
        assertEquals(WallpaperSource.Slideshow(File("/w"), 120), AmbientSettingsRows.step(slide, "slideshowInterval", +1).source)
        assertEquals(WallpaperSettings(), AmbientSettingsRows.step(WallpaperSettings(), "slideshowInterval", +1))
    }

    @Test fun pixelShift_defaultsOn_andRoundTrips() {
        assertTrue(WallpaperSettings().pixelShift)
        assertTrue(WallpaperSettings.fromJson("{}").pixelShift)
        val s = WallpaperSettings(pixelShift = false)
        assertEquals(s, WallpaperSettings.fromJson(s.toJson()))
    }

    @Test fun everyRow_hasTitle() {
        AmbientSettingsRows.rows(WallpaperSettings(notificationsEnabled = true), false).forEach { assertNotNull(it.title); assertTrue(it.title.isNotBlank()) }
    }
}
