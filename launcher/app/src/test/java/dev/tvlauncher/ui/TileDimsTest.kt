package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileDimsTest {
    @Test
    fun medium_isTodaysTile() {
        val d = TileSpec.dims(1f)
        assertEquals(TileSpec.WIDTH_DP, d.widthDp)
        assertEquals(TileSpec.HEIGHT_DP, d.heightDp)
        assertEquals(TileSpec.ICON_DP, d.iconDp)
        assertEquals(TileSpec.VIEW_WIDTH_DP, d.viewWidthDp)
        assertEquals(TileSpec.VIEW_HEIGHT_DP, d.viewHeightDp)
    }

    @Test
    fun scaled_keeps16by9_andGrowsMonotonically() {
        val small = TileSpec.dims(0.85f)
        val large = TileSpec.dims(1.2f)
        for (d in listOf(small, large)) {
            val ratio = d.widthDp.toFloat() / d.heightDp
            assertTrue("16:9 within 1%: $d", kotlin.math.abs(ratio - 16f / 9f) < 0.02f)
        }
        assertTrue(small.widthDp < TileSpec.WIDTH_DP && TileSpec.WIDTH_DP < large.widthDp)
        assertTrue(small.iconDp < TileSpec.ICON_DP && TileSpec.ICON_DP < large.iconDp)
        assertEquals(TileSpec.LABEL_HEIGHT_DP, large.viewHeightDp - large.heightDp - 2 * TileSpec.PAD_DP - TileSpec.LABEL_GAP_DP)
    }

    @Test
    fun motionOff_zeroesDurations() {
        val was = Motion.enabled
        try {
            Motion.enabled = false
            assertEquals(0L, Motion.ms(TileSpec.FOCUS_MS))
            Motion.enabled = true
            assertEquals(TileSpec.FOCUS_MS, Motion.ms(TileSpec.FOCUS_MS))
        } finally {
            Motion.enabled = was
        }
    }
}
