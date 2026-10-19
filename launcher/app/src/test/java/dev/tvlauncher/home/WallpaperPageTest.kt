package dev.tvlauncher.home

import dev.tvlauncher.ambient.UserCollection
import dev.tvlauncher.ambient.WallpaperCatalog
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
        assertTrue(spec.primaryRows.size <= 5) // collection, slideshow, interval, dim, own pictures
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

    @Test fun strip_listsCatalogCollectionsThenOwnerFolders_withUniqueKeys() {
        val none = WallpaperPage.collections(emptyList())
        assertEquals(WallpaperCatalog.COLLECTIONS, none.map { it.title })
        assertEquals(WallpaperCatalog.ALL.size, none.sumOf { it.entries.size })
        val dir = File("/x/Trips")
        val files = (1..20).map { File(dir, "p$it.jpg") }
        val all = WallpaperPage.collections(listOf(UserCollection("Trips", dir, files)))
        assertEquals(WallpaperCatalog.COLLECTIONS + "Trips", all.map { it.title })
        assertEquals(WallpaperPage.MAX_USER_THUMBS, all.last().entries.size)
        assertEquals(WallpaperSource.UserImage(files[0]), all.last().entries[0].source)
        val keys = all.flatMap { it.entries }.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        // The current wallpaper picks the collection shown first.
        val user = listOf(UserCollection("Trips", dir, files))
        assertEquals(all.size - 1, WallpaperPage.collectionOf(WallpaperSource.UserImage(files[3]), all, user))
        val anime = WallpaperCatalog.inCollection(WallpaperCatalog.ANIME).first()
        assertEquals(WallpaperCatalog.COLLECTIONS.indexOf(WallpaperCatalog.ANIME),
            WallpaperPage.collectionOf(WallpaperSource.BuiltIn(anime.id), all, user))
    }

    @Test fun slideshow_togglesWithinTheCurrentCollection() {
        val dir = File("/x/Trips")
        val files = listOf(File(dir, "a.jpg"), File(dir, "b.jpg"))
        val user = listOf(UserCollection("Trips", dir, files))
        val on = WallpaperPage.toggledSlideshow(WallpaperSource.UserImage(files[1]), user, 60)
        assertEquals(WallpaperSource.Slideshow(dir, 60), on)
        assertEquals(WallpaperSource.UserImage(files[0]), WallpaperPage.toggledSlideshow(on, user, 60))
        val anime = WallpaperCatalog.inCollection(WallpaperCatalog.ANIME)
        val bOn = WallpaperPage.toggledSlideshow(WallpaperSource.BuiltIn(anime[2].id), user, 120)
        assertEquals(WallpaperSource.BuiltInSlideshow(WallpaperCatalog.ANIME, 120), bOn)
        assertTrue(WallpaperPage.isSlideshow(bOn))
        assertEquals(WallpaperSource.BuiltIn(anime[0].id), WallpaperPage.toggledSlideshow(bOn, user, 120))
        assertEquals(WallpaperSource.BuiltInSlideshow(WallpaperCatalog.ANIME, 300), WallpaperPage.withInterval(bOn, 300))
    }

    @Test fun userText_hasNoDeveloperCommands() {
        assertTrue(!WallpaperPage.FOLDER_NOTE.contains("adb"))
    }

    @Test fun interval_steps_clamp() {
        assertEquals(60, WallpaperPage.stepInterval(30, 1))
        assertEquals(30, WallpaperPage.stepInterval(30, -1))
        assertEquals(600, WallpaperPage.stepInterval(600, 1))
        assertEquals("30 s", WallpaperPage.intervalLabel(30))
        assertEquals("5 min", WallpaperPage.intervalLabel(300))
    }
}
