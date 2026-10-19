package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientLayoutModelTest {
    @Test fun hd720_tvdpi() {
        val l = AmbientLayoutModel.compute(1280, 720, 1.3333f)
        // 960x540 dp canvas: margins 48+12 / 27+12 dp.
        assertEquals(80, l.contentLeft)
        assertEquals(1200, l.contentRight)
        assertEquals(52, l.contentTop)
        assertEquals(668, l.contentBottom)
        assertEquals(16, l.shiftMaxPx)
        assertEquals(129.6f, l.clockSp, 0.01f)
        assertTrue(l.showForecast)
        assertEquals(3, l.maxChips)
    }

    @Test fun fhd1080_xhdpi_matchesSameDpLayoutTwiceTheSize() {
        val l = AmbientLayoutModel.compute(1920, 1080, 2f)
        assertEquals(120, l.contentLeft)
        assertEquals(1800, l.contentRight)
        assertEquals(78, l.contentTop)
        assertEquals(1002, l.contentBottom)
        assertEquals(24, l.shiftMaxPx)
        assertEquals(129.6f, l.clockSp, 0.01f)
        assertEquals(l.weatherIconPx, 2 * AmbientLayoutModel.compute(960, 540, 1f).weatherIconPx)
        assertTrue(l.showForecast)
    }

    @Test fun uhd4k_density4_sameDp() {
        val l = AmbientLayoutModel.compute(3840, 2160, 4f)
        assertEquals(240, l.contentLeft)
        assertEquals(48, l.shiftMaxPx)
        assertEquals(129.6f, l.clockSp, 0.01f)
        assertTrue(l.showForecast)
    }

    @Test fun uhd4k_density2_largeCanvas_clockCapsAt140() {
        val l = AmbientLayoutModel.compute(3840, 2160, 2f)
        assertEquals(140f, l.clockSp, 0f)
        assertTrue(l.showForecast)
    }

    @Test fun smallScreen_clockFloorsAt96_andHidesForecast() {
        val l = AmbientLayoutModel.compute(1280, 720, 2f) // 640x360 dp
        assertEquals(96f, l.clockSp, 0f)
        assertFalse(l.showForecast)
    }

    @Test fun lite_hidesForecast() {
        assertFalse(AmbientLayoutModel.compute(1920, 1080, 2f, lite = true).showForecast)
    }

    @Test fun shiftedContent_neverLeavesSafeMargins() {
        for ((w, h, d) in listOf(Triple(1280, 720, 1.3333f), Triple(1920, 1080, 2f), Triple(3840, 2160, 4f))) {
            val l = AmbientLayoutModel.compute(w, h, d)
            val safeH = (AmbientLayoutModel.SAFE_H_DP * d).toInt()
            val safeV = (AmbientLayoutModel.SAFE_V_DP * d).toInt()
            assertTrue(l.contentLeft - l.shiftMaxPx >= safeH)
            assertTrue(l.contentRight + l.shiftMaxPx <= w - safeH)
            assertTrue(l.contentTop - l.shiftMaxPx >= safeV)
            assertTrue(l.contentBottom + l.shiftMaxPx <= h - safeV)
        }
    }

    @Test fun portraitMetrics_areTreatedAsLandscape() {
        val a = AmbientLayoutModel.compute(1080, 1920, 2f)
        val b = AmbientLayoutModel.compute(1920, 1080, 2f)
        assertEquals(b, a)
    }

    @Test fun tinyCanvas_contentBoxNeverInverted() {
        for ((w, h) in listOf(100 to 50, 120 to 120, 1 to 1, 200 to 90)) {
            val l = AmbientLayoutModel.compute(w, h, 1f)
            assertTrue("$w x $h", l.contentLeft <= l.contentRight)
            assertTrue("$w x $h", l.contentTop <= l.contentBottom)
            assertTrue(l.contentLeft >= 0 && l.contentRight <= l.widthPx)
            assertTrue(l.contentTop >= 0 && l.contentBottom <= l.heightPx)
        }
    }

    @Test fun zeroSize_isSafe() {
        val l = AmbientLayoutModel.compute(0, 0, 0f)
        assertTrue(l.clockSp in 96f..140f)
    }

    @Test fun typeHierarchy_clockBiggest() {
        val l = AmbientLayoutModel.compute(1920, 1080, 2f)
        assertTrue(l.clockSp > l.tempSp)
        assertTrue(l.tempSp > l.dateSp)
        assertTrue(l.dateSp > l.metaSp)
        assertTrue(l.chipMaxWidthPx > 0)
    }
}
