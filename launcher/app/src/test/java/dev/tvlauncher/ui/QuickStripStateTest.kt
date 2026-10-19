package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStripStateTest {
    @Test fun strip_appearsAfter700ms_withoutMovement() {
        val s = QuickStripState()
        assertEquals(700L, QuickStripState.DWELL_MS)
        s.onTileFocused(3, 1000)
        assertFalse(s.poll(1699))
        assertFalse(s.visible)
        assertTrue(s.poll(1700))
        assertTrue(s.visible)
        assertEquals(3, s.position)
        // Already shown: poll does not report a second appearance.
        assertFalse(s.poll(2500))
    }

    @Test fun strip_resetsOnFocusMove() {
        val s = QuickStripState()
        s.onTileFocused(3, 0)
        s.onTileFocused(4, 500)
        assertFalse(s.poll(700))
        assertTrue(s.poll(1200))
        assertEquals(4, s.position)
        // A visible strip hides as soon as focus moves to another tile.
        s.onTileFocused(5, 1300)
        assertFalse(s.visible)
        // A D-pad press that does not move focus also restarts the dwell.
        s.onDpad(1900)
        assertFalse(s.poll(2000))
        assertTrue(s.poll(2600))
    }

    @Test fun strip_hiddenWhileSpotlightOpen() {
        val s = QuickStripState()
        s.onTileFocused(1, 0)
        s.spotlightOpen = true
        assertFalse(s.poll(5000))
        assertFalse(s.visible)
        s.spotlightOpen = false
        s.onTileFocused(1, 6000)
        assertTrue(s.poll(6700))
        s.spotlightOpen = true
        assertFalse(s.visible)
    }

    @Test fun focusLeavingShelf_hidesAndStops() {
        val s = QuickStripState()
        s.onTileFocused(2, 0)
        s.onFocusLeftShelf()
        assertFalse(s.poll(1000))
        assertEquals(QuickStripState.NONE, s.position)
    }

    @Test fun returningFromStrip_keepsStripOnSameTile() {
        val s = QuickStripState()
        s.onTileFocused(2, 0)
        assertTrue(s.poll(700))
        s.onEnterStrip()
        assertTrue(s.inStrip)
        s.onTileFocused(2, 900) // UP/BACK back to the same tile
        assertTrue(s.visible)
        assertFalse(s.inStrip)
    }
}
