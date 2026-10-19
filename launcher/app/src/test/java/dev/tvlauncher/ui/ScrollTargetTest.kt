package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollTargetTest {
    @Test fun visible_row_needs_no_scroll() = assertEquals(0, ScrollTarget.compute(100, 150, 0, 500, 20))
    @Test fun row_below_scrolls_down_with_margin() = assertEquals(70, ScrollTarget.compute(500, 550, 0, 500, 20))
    @Test fun row_above_scrolls_up_with_margin() = assertEquals(-100, ScrollTarget.compute(120, 170, 200, 700, 20))
    @Test fun row_within_margin_of_edge_scrolls_a_bit() = assertEquals(-10, ScrollTarget.compute(210, 260, 200, 700, 20))
    @Test fun oversized_row_aligns_top() = assertEquals(180, ScrollTarget.compute(200, 900, 0, 500, 20))
}
