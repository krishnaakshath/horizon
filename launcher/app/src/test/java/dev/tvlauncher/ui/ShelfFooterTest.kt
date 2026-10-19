package dev.tvlauncher.ui

import dev.tvlauncher.discover.DiscoverSettings
import dev.tvlauncher.discover.DiscoverTileSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfFooterTest {
    @Test fun footer_followsTheLastApp() {
        assertEquals(6, ShelfFooter.itemCount(5, footer = true))
        assertEquals(5, ShelfFooter.position(5, footer = true))
        assertTrue(ShelfFooter.isFooter(5, 5, footer = true))
        for (i in 0 until 5) assertFalse(ShelfFooter.isFooter(i, 5, footer = true))
    }

    @Test fun noFooter_whenOff() {
        assertEquals(5, ShelfFooter.itemCount(5, footer = false))
        assertEquals(-1, ShelfFooter.position(5, footer = false))
        assertFalse(ShelfFooter.isFooter(5, 5, footer = false))
    }

    @Test fun emptyShelf_hasNoFooter() {
        assertFalse(ShelfFooter.show(wanted = true, apps = 0))
        assertEquals(0, ShelfFooter.itemCount(0, footer = true))
        assertEquals(-1, ShelfFooter.position(0, footer = true))
        assertTrue(ShelfFooter.show(wanted = true, apps = 1))
    }

    @Test fun setting_drivesTheTile() {
        assertTrue(DiscoverTileSpec.shouldAppend(DiscoverSettings(), editing = false))
        assertFalse(DiscoverTileSpec.shouldAppend(DiscoverSettings(showGetMoreAppsTile = false), editing = false))
    }
}
