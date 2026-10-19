package dev.tvlauncher.ui

import dev.tvlauncher.homelayout.HomeLayoutSettings
import dev.tvlauncher.homelayout.HomeStyle
import dev.tvlauncher.homelayout.LayoutStyle
import dev.tvlauncher.homelayout.ListKind
import dev.tvlauncher.homelayout.ScreenDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The focused tile (scaled view, ring, label) and the quick-strip stay inside the overscan-safe area. */
class SafeAreaShelfTest {
    private val screens = listOf(ScreenDp.TV, ScreenDp.MDPI_720, ScreenDp.XHDPI_4K)

    @Test fun focusedFirstTile_staysInsideTheSafeMargin_everyStyleAndScreen() {
        for (st in HomeStyle.values()) for (sc in listOf(0.85f, 1f, 1.2f)) for (scr in screens) {
            val p = LayoutStyle.plan(HomeLayoutSettings(style = st), sc, scr)
            val viewLeft = LayoutStyle.listPadStartDp(p.dims).toFloat()
            val focusedLeft = viewLeft - p.dims.viewWidthDp * (TileSpec.FOCUS_SCALE - 1f) / 2f
            assertTrue("$st x$sc ${scr.widthDp}: $focusedLeft", focusedLeft >= LayoutStyle.SAFE_H_DP)
            if (p.kind == ListKind.GRID) {
                // Every grid column is on screen: the focused right column stays inside too.
                val lastViewRight = viewLeft + p.columns * p.pitchDp - TileSpec.DECOR_DP
                val focusedRight = lastViewRight + p.dims.viewWidthDp * (TileSpec.FOCUS_SCALE - 1f) / 2f
                assertTrue("$st x$sc ${scr.widthDp}: $focusedRight", focusedRight <= scr.widthDp - LayoutStyle.SAFE_H_DP)
            }
        }
    }

    @Test fun singleRow_keepsItsTileCounts() {
        assertEquals(4, LayoutStyle.plan(HomeLayoutSettings(), 1f, ScreenDp.TV).columns)
        assertEquals(8, LayoutStyle.plan(HomeLayoutSettings(), 1f, ScreenDp.XHDPI_4K).columns)
    }

    @Test fun quickStrip_isClampedIntoTheSafeArea() {
        // 1280 px host, 64 px safe margin (48 dp at 1.33), a 290 px strip centered on a tile at x=40.
        assertEquals(64f, QuickStripState.Geometry.stripLeft(40f, 290f, 1280f, 64f, 64f), 0f)
        assertEquals(1280f - 64f - 290f, QuickStripState.Geometry.stripLeft(1270f, 290f, 1280f, 64f, 64f), 0f)
        assertEquals(500f, QuickStripState.Geometry.stripLeft(645f, 290f, 1280f, 64f, 64f), 0f)
    }
}
