package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopBarPillNavTest {
    @Test fun horizontal_staysInsideThePillRow() {
        assertEquals(TopBarNav.Horizontal.CONSUME, TopBarNav.onHorizontal(index = 0, count = 4, right = false))
        assertEquals(TopBarNav.Horizontal.CONSUME, TopBarNav.onHorizontal(index = 3, count = 4, right = true))
        assertEquals(TopBarNav.Horizontal.MOVE, TopBarNav.onHorizontal(index = 1, count = 4, right = false))
        assertEquals(TopBarNav.Horizontal.MOVE, TopBarNav.onHorizontal(index = 0, count = 4, right = true))
        assertEquals(TopBarNav.Horizontal.CONSUME, TopBarNav.onHorizontal(index = 0, count = 1, right = true))
    }

    @Test fun up_returnsToTheRememberedPill_whenStillShown() {
        assertEquals("net", TopBarNav.upTarget("net", listOf("bt", "net", "inputs", "settings")))
        assertNull(TopBarNav.upTarget("bt", listOf("net", "inputs", "settings")))
        assertNull(TopBarNav.upTarget(null, listOf("net", "inputs")))
    }
}
