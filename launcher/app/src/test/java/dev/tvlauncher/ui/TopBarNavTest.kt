package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TopBarNavTest {
    @Test fun shelfEmpty_opensSpotlight() {
        assertEquals(TopBarNav.Action.OPEN_SPOTLIGHT, TopBarNav.onDown(shelfEmpty = true))
    }

    @Test fun shelfNotEmpty_focusesShelf() {
        assertEquals(TopBarNav.Action.FOCUS_SHELF, TopBarNav.onDown(shelfEmpty = false))
    }
}
