package dev.tvlauncher.ambient

import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.Trailing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WallpaperPanelModelTest {
    private fun keys(e: List<PanelEntry>) = e.filterIsInstance<PanelEntry.Row>().map { it.key }

    @Test fun defaults_slideshowDimChips() {
        val e = WallpaperPanelModel.entries(WallpaperSettings(), accessGranted = true, hasPictures = true, accessNote = null, folderPath = "/p")
        assertEquals(listOf("slideshow", "dim", "notificationsEnabled") + textRowIfOn(WallpaperSettings()), keys(e))
        assertTrue(e.last() is PanelEntry.Message)
        assertTrue((e.last() as PanelEntry.Message).text.contains("/p"))
    }

    private fun textRowIfOn(s: WallpaperSettings) = if (s.notificationsEnabled) listOf("showNotificationText") else emptyList()

    @Test fun slideshow_showsIntervalStepper() {
        val s = WallpaperSettings(source = WallpaperSource.Slideshow(File("/p"), 120))
        val rows = WallpaperPanelModel.entries(s, true, true, null, "/p").filterIsInstance<PanelEntry.Row>()
        val interval = rows.first { it.key == WallpaperPanelModel.INTERVAL }
        assertEquals(WallpaperPanelModel.stepper("2 min"), interval.value)
        assertEquals(Trailing.Switch(true), rows.first { it.key == WallpaperPanelModel.SLIDESHOW }.trailing)
    }

    @Test fun noPictures_disablesSlideshowAndSaysSoFirst() {
        val e = WallpaperPanelModel.entries(WallpaperSettings(), true, hasPictures = false, accessNote = null, folderPath = "/p")
        assertEquals("msg:pictures", e.first().key)
        assertFalse((e.first { it.key == WallpaperPanelModel.SLIDESHOW } as PanelEntry.Row).enabled)
    }

    @Test fun accessRowOnlyWhenChipsOnAndNotGranted() {
        val on = WallpaperSettings(notificationsEnabled = true)
        assertTrue(WallpaperPanelModel.ACCESS in keys(WallpaperPanelModel.entries(on, false, true, null, "/p")))
        assertFalse(WallpaperPanelModel.ACCESS in keys(WallpaperPanelModel.entries(on, true, true, null, "/p")))
        val off = WallpaperSettings(notificationsEnabled = false)
        assertFalse(WallpaperPanelModel.ACCESS in keys(WallpaperPanelModel.entries(off, false, true, null, "/p")))
        val withNote = WallpaperPanelModel.entries(on, false, true, "Do this", "/p")
        assertTrue(withNote.any { it is PanelEntry.Message && it.text == "Do this" })
    }

    @Test fun dimStepperValue() {
        val e = WallpaperPanelModel.entries(WallpaperSettings(dimPercent = 30), true, true, null, "/p")
        assertEquals(WallpaperPanelModel.stepper("30 %"), (e.first { it.key == WallpaperPanelModel.DIM } as PanelEntry.Row).value)
        assertEquals(setOf(WallpaperPanelModel.INTERVAL, WallpaperPanelModel.DIM), WallpaperPanelModel.STEPPERS)
    }
}
