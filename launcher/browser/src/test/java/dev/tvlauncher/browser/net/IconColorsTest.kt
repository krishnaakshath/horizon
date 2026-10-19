package dev.tvlauncher.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconColorsTest {
    @Test fun dominant_picksMostCommonSaturatedHue() {
        val red = 0xFFE01010.toInt(); val blue = 0xFF1030E0.toInt(); val white = -1
        val px = IntArray(100) { if (it < 50) white else if (it < 80) red else blue }
        assertEquals(red, IconColors.dominant(px))
    }

    @Test fun dominant_grayOrEmpty_isNull() {
        assertEquals(null, IconColors.dominant(IntArray(10) { 0xFF808080.toInt() }))
        assertEquals(null, IconColors.dominant(IntArray(0)))
    }

    @Test fun letterColor_isStable_andFromPalette() {
        val a = IconColors.letterColor("github.com")
        assertEquals(a, IconColors.letterColor("github.com"))
        assertTrue(a in IconColors.PALETTE)
        assertNotEquals(IconColors.letterColor("a.com"), IconColors.letterColor("b.com"))
    }

    @Test fun letterFor_skipsWww() {
        assertEquals('G', IconColors.letterFor("www.github.com"))
        assertEquals('?', IconColors.letterFor(""))
    }
}
