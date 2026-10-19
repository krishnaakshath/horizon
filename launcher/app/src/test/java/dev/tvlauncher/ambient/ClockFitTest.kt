package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockFitTest {
    /** Generous upper bound for a light geometric sans: digits 0.62 em, colon 0.30 em. */
    private fun widthEm(s: String) = s.sumOf { if (it == ':') 0.30 else 0.62 }.toFloat()

    private fun spToPx(sp: Float, density: Float) = sp * density

    @Test fun widestSample_coversEveryTimeInBothFormats() {
        assertEquals("88:88", ClockFit.widestSample(true, '8'))
        assertEquals("18:88", ClockFit.widestSample(false, '8'))
        assertEquals('4', ClockFit.widestDigit { if (it == '4') 2f else 1f })
    }

    @Test fun fitSp_keepsBaseWhenItFits_andShrinksWhenItDoesNot() {
        assertEquals(140f, ClockFit.fitSp(140f, 48f, 1000) { it * 4f }, 0f) // 560 px
        val shrunk = ClockFit.fitSp(140f, 48f, 300) { it * 4f } // needs <= 75 sp
        assertTrue(shrunk <= 75f && shrunk >= 72f)
        assertEquals(48f, ClockFit.fitSp(140f, 48f, 10) { it * 4f }, 0f)
        assertEquals(140f, ClockFit.fitSp(140f, 48f, 0) { it * 4f }, 0f)
    }

    /** 720p, 1080p and 4K TVs (densities 1.0/1.5/2.0/3.0 as TVs report them), both clock formats. */
    @Test fun clockFitsContentBox_atEveryTvResolution_withoutShrinking() {
        val screens = listOf(Triple(1280, 720, 1.0f), Triple(1280, 720, 2.0f / 1.5f), Triple(1920, 1080, 1.5f),
            Triple(1920, 1080, 2.0f), Triple(3840, 2160, 3.0f), Triple(3840, 2160, 4.0f))
        for ((w, h, d) in screens) for (lite in listOf(true, false)) for (h24 in listOf(true, false)) {
            val m = AmbientLayoutModel.compute(w, h, d, lite)
            val sample = ClockFit.widestSample(h24, '8')
            val available = m.contentRight - m.contentLeft
            val sp = ClockFit.fitSp(m.clockSp, 48f, available) { spToPx(it, d) * widthEm(sample) }
            assertEquals("${w}x$h@$d h24=$h24", m.clockSp, sp, 0f)
            assertTrue(spToPx(sp, d) * widthEm(sample) <= available)
        }
    }
}
