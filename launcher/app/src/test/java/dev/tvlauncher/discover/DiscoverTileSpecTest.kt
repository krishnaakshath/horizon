package dev.tvlauncher.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverTileSpecTest {
    @Test fun spec_basics() {
        assertEquals("Get more apps", DiscoverTileSpec.LABEL)
        assertTrue(DiscoverTileSpec.KEY.startsWith("discover:"))
        assertTrue(DiscoverTileSpec.DASH_DP >= 4)
        assertTrue(DiscoverTileSpec.PLUS_DP in 24..64)
    }

    @Test fun appendDecision() {
        assertTrue(DiscoverTileSpec.shouldAppend(DiscoverSettings(), editing = false))
        assertFalse(DiscoverTileSpec.shouldAppend(DiscoverSettings(showGetMoreAppsTile = false), editing = false))
        assertFalse(DiscoverTileSpec.shouldAppend(DiscoverSettings(), editing = true))
    }

    @Test fun grid_rowsAndColumns() {
        // 2-row horizontal grid: item i goes to column i / 2, row i % 2.
        assertEquals(0 to 0, DiscoverGrid.cell(0))
        assertEquals(0 to 1, DiscoverGrid.cell(1))
        assertEquals(1 to 0, DiscoverGrid.cell(2))
        assertEquals(3, DiscoverGrid.columns(5))
        assertEquals(0, DiscoverGrid.columns(0))
    }

    @Test fun letterTile_initials() {
        assertEquals("N", DiscoverGrid.initials("Netflix"))
        assertEquals("YM", DiscoverGrid.initials("YouTube Music"))
        assertEquals("P+", DiscoverGrid.initials("Paramount+"))
        assertEquals("D+", DiscoverGrid.initials("Disney+"))
        assertEquals("SL", DiscoverGrid.initials("Sony LIV"))
        assertEquals("?", DiscoverGrid.initials("  "))
    }
}
