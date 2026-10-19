package dev.tvlauncher.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelModelTest {
    @Test fun bodyHeight_isSevenRowsOnTheEightDpGrid() {
        assertEquals(7 * 48, PanelLimits.bodyMaxDp())
        assertEquals(0, PanelLimits.ROW_DP % 8)
        assertEquals(0, PanelLimits.HEADER_DP % 8)
        assertEquals(48, PanelLimits.bodyMaxDp(0)) // never zero
    }

    @Test fun visibleRows_clampedBetweenOneAndSeven() {
        assertEquals(1, PanelLimits.visibleRows(10))
        assertEquals(3, PanelLimits.visibleRows(150))
        assertEquals(7, PanelLimits.visibleRows(10_000))
    }

    @Test fun sidePanelFitsUnderTheTopBarOnA540dpScreen() {
        // 960x540 dp TV: 27 dp overscan top and bottom plus 56 dp top-bar clearance.
        val available = 540 - 27 - 27 - 56
        assertTrue(PanelLimits.cardDp(100) <= available)
        assertEquals(PanelLimits.cardDp(7), PanelLimits.cardDp(30))
    }

    @Test fun focusIndex_keepsFocusedKeyThenPreferredThenFirst() {
        val keys = listOf("a", "b", "c")
        assertEquals(2, PanelLimits.focusIndex(keys, "c", "b"))
        assertEquals(1, PanelLimits.focusIndex(keys, "gone", "b"))
        assertEquals(0, PanelLimits.focusIndex(keys, null, "missing"))
        assertEquals(-1, PanelLimits.focusIndex(emptyList(), "a", "b"))
    }

    @Test fun motion_isCalmAndHonoursScaleAndLite() {
        assertEquals(220L, PanelMotion.duration(lite = false, scale = 1f))
        assertEquals(160L, PanelMotion.duration(lite = true, scale = 1f))
        assertEquals(110L, PanelMotion.duration(lite = false, scale = 0.5f))
        assertEquals(0L, PanelMotion.duration(lite = false, scale = 0f))
        assertEquals(0L, PanelMotion.duration(lite = false, scale = 1f, enabled = false))
        assertEquals(0L, PanelMotion.duration(lite = true, scale = Float.NaN))
        assertEquals(165L, PanelMotion.closeDuration(lite = false, scale = 1f))
        assertTrue(PanelMotion.duration(false, 1f) in 160L..220L)
    }

    @Test fun content_splitsRowsAndMessages() {
        val c = PanelContent(PanelHeader(null, "T", "s"), listOf(
            PanelEntry.Row("r1", null, "One"),
            PanelEntry.Message("m", "Hi"),
            PanelEntry.Row("r2", null, "Two", trailing = Trailing.Check)
        ))
        assertEquals(listOf("r1", "r2"), c.rows.map { it.key })
        assertEquals(listOf("m"), c.messages.map { it.key })
    }

    @Test fun surfaceIsNearOpaqueForTheAuditor() {
        assertTrue((PanelStyle.SURFACE ushr 24) / 255f >= 0.95f)
        assertTrue((PanelStyle.SURFACE_LITE ushr 24) / 255f >= 0.95f)
        assertTrue(PanelStyle.DETAIL_SP >= 14f)
        assertTrue(PanelStyle.SECTION_SP >= 14f)
    }
}
