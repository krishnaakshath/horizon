package dev.tvlauncher.ambient

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarFieldTest {
    @Test fun sameSeed_sameStars() {
        val a = StarField.generate(42, 200, 1920, 1080)
        val b = StarField.generate(42, 200, 1920, 1080)
        assertArrayEquals(a, b, 0f)
    }

    @Test fun differentSeed_differentStars() {
        val a = StarField.generate(1, 50, 1920, 1080)
        val b = StarField.generate(2, 50, 1920, 1080)
        assertFalse(a.contentEquals(b))
    }

    @Test fun starsInsideBounds_withSaneRadiusAndAlpha() {
        val s = StarField.generate(9, 300, 1280, 720)
        assertEquals(300 * StarField.STRIDE, s.size)
        for (i in 0 until 300) {
            val x = s[i * 4]; val y = s[i * 4 + 1]; val r = s[i * 4 + 2]; val a = s[i * 4 + 3]
            assertTrue(x in 0f..1280f); assertTrue(y in 0f..720f)
            assertTrue(r in 0.4f..2.2f); assertTrue(a in 0.15f..1f)
        }
    }

    @Test fun count_scalesWithArea() {
        assertEquals(StarField.countFor(1920, 1080) / 4, StarField.countFor(960, 540), 2)
        assertTrue(StarField.countFor(1920, 1080) <= 400)
    }

    private fun assertEquals(a: Int, b: Int, tol: Int) = assertTrue("$a vs $b", kotlin.math.abs(a - b) <= tol)
}
