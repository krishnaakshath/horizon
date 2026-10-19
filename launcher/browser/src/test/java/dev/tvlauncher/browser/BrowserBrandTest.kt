package dev.tvlauncher.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrowserBrandTest {
    private val pathChars = Regex("^[MmLlHhVvAaCcSsQqZz0-9 ,.\\-]+$")

    @Test fun pathData_isWellFormed() {
        (BrowserBrand.MARK_PATHS + BrowserBrand.WORDMARK_PATHS.values).forEach {
            assertTrue(it, it.isNotBlank() && pathChars.matches(it) && it.trimStart().startsWith("M"))
        }
    }

    @Test fun wordmark_spellsBrowser() {
        assertEquals("Browser", BrowserBrand.WORDMARK.joinToString("") { it.first.toString() })
    }

    @Test fun gradient_isThreeOpaqueColors() {
        assertEquals(3, BrowserBrand.GRADIENT.size)
        BrowserBrand.GRADIENT.forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test fun xmlAssets_useTheBrandPaths() {
        val res = File("src/main/res")
        val mark = File(res, "drawable/brand_mark.xml").readText()
        BrowserBrand.MARK_PATHS.forEach { assertTrue("mark missing $it", mark.contains(it)) }
        val banner = File(res, "drawable/banner_browser.xml").readText()
        BrowserBrand.MARK_PATHS.forEach { assertTrue("banner missing $it", banner.contains(it)) }
        BrowserBrand.WORDMARK_PATHS.values.forEach { assertTrue("banner wordmark", banner.contains(it)) }
        assertTrue(File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText().contains("adaptive-icon"))
    }
}
