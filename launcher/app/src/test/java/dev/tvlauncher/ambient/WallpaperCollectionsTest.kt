package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WallpaperCollectionsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun catalog_hasClassicAndAnime_withUniqueIds_andNoSeriesNames() {
        assertEquals(WallpaperCatalog.IDS.size, WallpaperCatalog.IDS.toSet().size)
        assertEquals(listOf(WallpaperCatalog.CLASSIC, WallpaperCatalog.ANIME), WallpaperCatalog.COLLECTIONS)
        assertTrue(WallpaperCatalog.inCollection(WallpaperCatalog.ANIME).size >= 4)
        assertEquals(WallpaperCatalog.IDS, WallpaperSource.BUILT_IN_IDS)
        val banned = listOf("dragon", "ball", "saiyan", "jujutsu", "kaisen", "titan", "naruto", "konoha", "goku", "attack")
        for (m in WallpaperCatalog.ALL) for (b in banned) {
            assertFalse("${m.title} names a series", m.title.lowercase().contains(b) || m.id.contains(b))
        }
    }

    @Test fun builtInSlideshow_roundTrips_andUnknownCollectionFallsBack() {
        val s = WallpaperSettings(source = WallpaperSource.BuiltInSlideshow("Anime", 120))
        assertEquals(s, WallpaperSettings.fromJson(s.toJson()))
        val bad = WallpaperSettings(source = WallpaperSource.BuiltInSlideshow("Nope", 120)).normalized()
        assertEquals(WallpaperSource.DEFAULT, bad.source)
        val clamped = WallpaperSettings(source = WallpaperSource.BuiltInSlideshow("Anime", 5)).normalized()
        assertEquals(WallpaperPlan.MIN_INTERVAL_SEC, (clamped.source as WallpaperSource.BuiltInSlideshow).intervalSec)
        assertEquals(s, s.sanitizedFor(emptyList()))
    }

    @Test fun animeBuiltIn_isKeptByNormalize() {
        val s = WallpaperSettings(source = WallpaperSource.BuiltIn("golden_aura"))
        assertEquals(s, WallpaperSettings.fromJson(s.toJson()))
    }

    @Test fun sanitize_acceptsAnyAllowedRoot() {
        val app = tmp.newFolder("wallpapers"); val shared = tmp.newFolder("TVLauncher")
        val inShared = WallpaperSettings(source = WallpaperSource.Slideshow(File(shared, "Anime"), 60))
        assertEquals(inShared, inShared.sanitizedFor(listOf(app, shared)))
        assertEquals(WallpaperSource.DEFAULT, inShared.sanitizedFor(listOf(app)).source)
        val escape = WallpaperSettings(source = WallpaperSource.UserImage(File(shared, "../x.jpg")))
        assertEquals(WallpaperSource.DEFAULT, escape.sanitizedFor(listOf(app, shared)).source)
    }

    private fun img(dir: File, name: String) = File(dir, name).apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }

    @Test fun scanner_folderPerCollection_looseAsMyPictures_usbSuffix_andDedupe() {
        val app = tmp.newFolder("app")
        img(app, "a.jpg")
        img(File(app, "golden_aura"), "1.png")
        File(app, "empty").mkdirs()
        img(File(app, ".hidden"), "x.jpg")
        val shared = tmp.newFolder("shared")
        img(File(shared, "Anime"), "z.webp"); img(File(shared, "Anime"), "notes.txt")
        val usb = tmp.newFolder("usb")
        img(File(usb, "Anime"), "y.jpg")
        val found = CollectionScanner.scan(listOf(
            CollectionScanner.Root(app, looseName = CollectionScanner.MY_PICTURES),
            CollectionScanner.Root(shared),
            CollectionScanner.Root(usb, suffix = " (USB)"),
            CollectionScanner.Root(File(tmp.root, "missing"))
        ))
        assertEquals(listOf("My pictures", "golden aura", "Anime", "Anime (USB)"), found.map { it.name })
        assertEquals(listOf("z.webp"), found[2].files.map { it.name })
    }

    @Test fun displayName_isReadable() {
        assertEquals("dragon ball z", CollectionScanner.displayName("dragon_ball-z"))
        assertEquals("Pictures", CollectionScanner.displayName("  "))
    }

    @Test fun settingsRows_labelSlideshows() {
        val anime = AmbientSettingsRows.rows(WallpaperSettings(source = WallpaperSource.BuiltInSlideshow("Anime", 60)), false)
        assertEquals("Anime slideshow", anime.first { it.key == "wallpaper" }.value)
        assertTrue(anime.first { it.key == "slideshowInterval" }.visible)
        val folder = AmbientSettingsRows.rows(WallpaperSettings(source = WallpaperSource.Slideshow(File("/x/TVLauncher/Cursed_Night"), 60)), false)
        assertEquals("Cursed Night slideshow", folder.first { it.key == "wallpaper" }.value)
        assertEquals("Golden Aura", AmbientSettingsRows.rows(WallpaperSettings(source = WallpaperSource.BuiltIn("golden_aura")), false)
            .first { it.key == "wallpaper" }.value)
        val stepped = AmbientSettingsRows.step(WallpaperSettings(source = WallpaperSource.BuiltInSlideshow("Anime", 60)), "slideshowInterval", 1)
        assertEquals(120, (stepped.source as WallpaperSource.BuiltInSlideshow).intervalSec)
    }
}

class TileColumnsTest {
    @Test fun columnsFitThePane() {
        // 720p Settings pane at density 1.33: ~600 px inner, 230 px cells -> 2 columns, not a clipped 4.
        assertEquals(2, WallpaperPlan.tileColumns(600, 230, 4))
        assertEquals(4, WallpaperPlan.tileColumns(2000, 230, 4))
        assertEquals(2, WallpaperPlan.tileColumns(100, 230, 4))
        assertEquals(4, WallpaperPlan.tileColumns(100, 0, 4))
    }
}
