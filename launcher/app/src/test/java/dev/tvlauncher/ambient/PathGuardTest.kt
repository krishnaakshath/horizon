package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PathGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun base(): File = tmp.newFolder("files", "wallpapers")

    @Test fun childFile_isInside() {
        val b = base()
        assertTrue(PathGuard.isInside(b, File(b, "a.jpg")))
        assertTrue(PathGuard.isInside(b, File(b, "sub/b.jpg")))
    }

    @Test fun dotDot_escape_isOutside() {
        val b = base()
        assertFalse(PathGuard.isInside(b, File(b, "../secret.jpg")))
        assertFalse(PathGuard.isInside(b, File(b, "x/../../secret.jpg")))
        assertTrue(PathGuard.isInside(b, File(b, "x/../ok.jpg")))
    }

    @Test fun prefixLookalike_isOutside() {
        val b = base()
        val evil = File(b.parentFile, "wallpapers-evil/a.jpg")
        assertFalse(PathGuard.isInside(b, evil))
    }

    @Test fun trailingSlash_onBase_andSameDir() {
        val b = base()
        assertTrue(PathGuard.isInside(File(b.path + "/"), File(b, "a.jpg")))
        assertFalse(PathGuard.isInside(b, b))
        assertTrue(PathGuard.isInside(b, File(b.path + "/"), allowSame = true))
    }

    @Test fun symlinkPointingOutside_isOutside() {
        val b = base()
        val outside = tmp.newFile("outside.jpg")
        val link = File(b, "link.jpg")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertFalse(PathGuard.isInside(b, link))
    }

    @Test fun nullBase_isOutside() {
        assertFalse(PathGuard.isInside(null, File("/tmp/a.jpg")))
    }

    @Test fun settings_sanitizedFor_dropsOutsidePaths() {
        val b = base()
        val inside = WallpaperSettings(source = WallpaperSource.UserImage(File(b, "a.jpg")), dimPercent = 20)
        assertEquals(inside, inside.sanitizedFor(b))
        val escaped = WallpaperSettings(source = WallpaperSource.UserImage(File(b, "../../x.jpg")), dimPercent = 20)
        assertEquals(WallpaperSettings(dimPercent = 20), escaped.sanitizedFor(b))
        val slide = WallpaperSettings(source = WallpaperSource.Slideshow(b, 60))
        assertEquals(slide, slide.sanitizedFor(b))
        val slideOut = WallpaperSettings(source = WallpaperSource.Slideshow(File("/etc"), 60))
        assertEquals(WallpaperSource.DEFAULT, slideOut.sanitizedFor(b).source)
        val noFolder = WallpaperSettings(source = WallpaperSource.UserImage(File(b, "a.jpg")))
        assertEquals(WallpaperSource.DEFAULT, noFolder.sanitizedFor(null).source)
        val builtIn = WallpaperSettings(source = WallpaperSource.BuiltIn("ocean"))
        assertEquals(builtIn, builtIn.sanitizedFor(null))
    }
}
