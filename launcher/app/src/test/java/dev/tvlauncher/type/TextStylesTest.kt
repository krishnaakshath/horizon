package dev.tvlauncher.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStylesTest {
    @Test fun sizesAreMonotone() {
        val s = TextStyles
        assertTrue(s.Display.sizeSp > s.Title.sizeSp)
        assertTrue(s.Title.sizeSp > s.Subtitle.sizeSp)
        assertTrue(s.Subtitle.sizeSp > s.Body.sizeSp)
        assertTrue(s.Body.sizeSp > s.Caption.sizeSp)
        assertTrue(s.Caption.sizeSp >= s.Label.sizeSp)
    }

    @Test fun tokensAreSane() {
        TextStyles.all.forEach {
            assertTrue(it.name, it.sizeSp in 10f..140f)
            assertTrue(it.name, it.letterSpacingEm in -0.02f..0.12f)
            assertTrue(it.name, it.lineHeightMult in 0.7f..1.1f)
        }
        assertEquals(TextStyles.all.size, TextStyles.all.map { it.name }.toSet().size)
    }

    @Test fun specifics() {
        assertEquals(FontWeight.SEMIBOLD, TextStyles.Display.weight)
        assertEquals(-0.005f, TextStyles.Display.letterSpacingEm, 1e-6f)
        assertTrue(TextStyles.Display.sizeSp in 44f..48f)
        assertEquals(0.06f, TextStyles.Label.letterSpacingEm, 1e-6f)
        assertTrue(TextStyles.Label.allCaps)
        assertEquals(112f, TextStyles.ClockLarge.sizeSp, 0f)
        assertEquals(FontWeight.LIGHT, TextStyles.ClockLarge.weight)
        listOf(TextStyles.ClockLarge, TextStyles.ClockSmall, TextStyles.Numeric).forEach {
            assertEquals("tnum", it.fontFeatures)
        }
        assertEquals(null, TextStyles.Body.fontFeatures)
    }
}
