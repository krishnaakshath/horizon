package dev.tvlauncher.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrowserBrandTest {
    private val res = File("src/main/res")
    private fun read(p: String) = File(res, p).readText()

    @Test fun name_followsTheFamily() {
        assertEquals("H02 Browser", BrowserBrand.NAME)
        assertEquals(0xFF, BrowserBrand.ACCENT ushr 24)
        val strings = read("values/strings.xml")
        assertTrue(strings.contains(">H02 Browser<"))
    }

    @Test fun xmlAssets_areTheGeneratedH02Art() {
        for (a in BrowserBrand.ASSETS) {
            val xml = read(a)
            assertTrue(a, xml.contains("Unbounded Medium") || a.endsWith("ic_launcher_bg.xml"))
            assertTrue(a, xml.contains("companion_art.py"))
            assertFalse(a, xml.contains("Orbit"))
        }
        // Glass card plus the violet sunrise on the banner.
        val banner = read("drawable/banner_browser.xml")
        assertTrue(banner.contains("#38FFFFFF") && banner.contains("#FF8B5CF6"))
        assertTrue(read("mipmap-anydpi-v26/ic_launcher.xml").contains("adaptive-icon"))
    }

    @Test fun reveal_targetsNamedPartsOfTheBase() {
        val base = read("drawable/brand_mark_anim_base.xml")
        val reveal = read("drawable/brand_mark_reveal.xml")
        Regex("""<target android:name="([^"]+)"""").findAll(reveal).map { it.groupValues[1] }.toList().also { assertTrue(it.isNotEmpty()) }
            .forEach { assertTrue(it, base.contains("android:name=\"$it\"")) }
    }

    @Test fun customCursor_isKept() {
        assertTrue(File(res, "drawable/ic_cursor.xml").isFile)
    }
}
