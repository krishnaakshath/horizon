package dev.tvlauncher.type

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontPlanTest {
    @Test fun everyWeightHasUniqueAssetAndFallback() {
        val entries = FontWeight.values().map { FontPlan.entry(it) }
        assertEquals(FontWeight.values().size, entries.map { it.assetName }.toSet().size)
        entries.forEach {
            assertTrue(it.assetName.startsWith("fonts/") && it.assetName.endsWith(".ttf"))
            assertTrue(it.fallbackFamily.startsWith("sans-serif"))
            assertTrue(it.fallbackStyle == 0 || it.fallbackStyle == 1)
        }
    }

    @Test fun fallbacksFollowWeightIntent() {
        assertEquals("sans-serif-light", FontPlan.entry(FontWeight.LIGHT).fallbackFamily)
        assertEquals("sans-serif-medium", FontPlan.entry(FontWeight.MEDIUM).fallbackFamily)
        assertEquals("sans-serif", FontPlan.entry(FontWeight.REGULAR).fallbackFamily)
        assertEquals(1, FontPlan.entry(FontWeight.BOLD).fallbackStyle)
    }

    @Test fun unknownInputIsRegular() {
        assertEquals(FontWeight.REGULAR, FontPlan.fromName(null))
        assertEquals(FontWeight.REGULAR, FontPlan.fromName(""))
        assertEquals(FontWeight.REGULAR, FontPlan.fromName("wat"))
        assertEquals(FontWeight.SEMIBOLD, FontPlan.fromName("  SemiBold "))
        assertEquals(FontWeight.LIGHT, FontPlan.fromName("light"))
    }

    @Test fun cssWeightsSnapToNearestBucket() {
        assertEquals(FontWeight.LIGHT, FontPlan.fromCssWeight(100))
        assertEquals(FontWeight.LIGHT, FontPlan.fromCssWeight(300))
        assertEquals(FontWeight.REGULAR, FontPlan.fromCssWeight(400))
        assertEquals(FontWeight.MEDIUM, FontPlan.fromCssWeight(500))
        assertEquals(FontWeight.SEMIBOLD, FontPlan.fromCssWeight(600))
        assertEquals(FontWeight.BOLD, FontPlan.fromCssWeight(900))
        assertEquals(FontWeight.REGULAR, FontPlan.fromCssWeight(-5))
    }

    @Test fun assetNamesMatchBundledFiles() {
        val dir = File("src/main/assets/fonts")
        assertTrue("missing ${dir.absolutePath}", dir.isDirectory)
        val actual = dir.listFiles()!!.filter { it.name.endsWith(".ttf") }.map { "fonts/" + it.name }.sorted()
        assertEquals(actual, FontPlan.assetNames().sorted())
        FontPlan.assetNames().forEach { assertTrue(it, File("src/main/assets/$it").length() > 10_000) }
    }

    @Test fun bundledFilesAreRealTtfAndWithinBudget() {
        var total = 0L
        FontPlan.assetNames().forEach {
            val f = File("src/main/assets/$it")
            total += f.length()
            val magic = f.inputStream().use { s -> ByteArray(4).also { b -> s.read(b) } }
            assertTrue(it, magic.contentEquals(byteArrayOf(0, 1, 0, 0)))
        }
        assertTrue("payload $total", total <= 450_000)
    }

    @Test fun licenseAndNoticeAreBundled() {
        assertTrue(File("src/main/res/raw/font_license_ofl.txt").readText().contains("SIL OPEN FONT LICENSE"))
        assertTrue(FontNotices.THIRD_PARTY.contains("Manrope"))
        assertTrue(FontNotices.THIRD_PARTY.contains("SIL Open Font License"))
    }
}
