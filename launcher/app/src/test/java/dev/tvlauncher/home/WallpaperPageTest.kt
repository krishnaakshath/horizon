package dev.tvlauncher.home

import dev.tvlauncher.ambient.BuiltInWallpapers
import dev.tvlauncher.ambient.WallpaperSource
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WallpaperPageTest {
    @Test fun spec_isWithinBudget_andRowGuardAcceptsThePage() {
        val spec = WallpaperPage.SPEC
        assertTrue(spec.primaryRows.size <= 5)
        assertTrue(spec.withinBudget)
        assertEquals(spec.primaryRows.size, spec.primaryRows.toSet().size)
        val guard = RowGuard(spec)
        for (k in spec.primaryRows) guard.claim(k, advanced = false)
        assertEquals(spec.primaryRows.size, guard.primaryCount)
        assertTrue(guard.primaryCount <= PageSpec.MAX_PRIMARY)
    }

    @Test fun noRowIsRepeatedOnTheAmbientPage() {
        val ambient = (WaveTwoSections.AMBIENT_SPEC.primaryRows + WaveTwoSections.AMBIENT_SPEC.advancedRows).toSet()
        for (k in WallpaperPage.SPEC.primaryRows + WallpaperPage.SPEC.advancedRows) assertTrue(k, k !in ambient)
        // The wallpaper's model rows are not on the Ambient page in any form.
        for (m in WallpaperPage.MODEL_KEYS) {
            assertTrue(m !in WaveTwoSections.AMBIENT_PRIMARY_MODEL_KEYS)
            assertTrue("ambient.$m" !in ambient)
        }
        // Notification chips, their text and the access card stay on Ambient (Advanced) only.
        val keys = WallpaperPage.SPEC.primaryRows.joinToString()
        for (n in listOf("notif", "Notification", "access")) assertTrue(!keys.contains(n))
        assertTrue("ambient.notificationsEnabled" in WaveTwoSections.AMBIENT_SPEC.advancedRows)
        assertTrue("ambient.showNotificationText" in WaveTwoSections.AMBIENT_SPEC.advancedRows)
    }

    @Test fun strip_listsBuiltInsThenMyPictures_withUniqueKeys() {
        val none = WallpaperPage.collections(emptyList())
        assertEquals(1, none.size)
        assertEquals(BuiltInWallpapers.ALL.size, none[0].entries.size)
        val files = listOf(File("/x/a.jpg"), File("/x/b.png"))
        val two = WallpaperPage.collections(files)
        assertEquals(listOf("Built-in", "My pictures"), two.map { it.title })
        assertEquals(WallpaperSource.UserImage(files[0]), two[1].entries[0].source)
        val keys = two.flatMap { it.entries }.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun interval_steps_clamp() {
        assertEquals(60, WallpaperPage.stepInterval(30, 1))
        assertEquals(30, WallpaperPage.stepInterval(30, -1))
        assertEquals(600, WallpaperPage.stepInterval(600, 1))
        assertEquals("30 s", WallpaperPage.intervalLabel(30))
        assertEquals("5 min", WallpaperPage.intervalLabel(300))
    }
}
