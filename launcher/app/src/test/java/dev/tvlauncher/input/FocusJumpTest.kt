package dev.tvlauncher.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusJumpTest {
    @Test fun pageSteps_isTheVisibleItemsMinusOne() {
        assertEquals(5, FocusJump.pageSteps(1800, 300)) // six tiles on the shelf
        assertEquals(6, FocusJump.pageSteps(700, 100)) // seven settings rows
        assertEquals(1, FocusJump.pageSteps(300, 300))
        assertEquals(1, FocusJump.pageSteps(200, 300)) // item bigger than the list
        assertEquals(FocusJump.MAX_STEPS, FocusJump.pageSteps(10_000, 10))
    }

    @Test fun pageSteps_unknownSizes_moveOne() {
        assertEquals(1, FocusJump.pageSteps(0, 100))
        assertEquals(1, FocusJump.pageSteps(1000, 0))
        assertEquals(1, FocusJump.pageSteps(-1, -1))
    }

    @Test fun target_clampsAtBothEnds() {
        assertEquals(7, FocusJump.target(2, 20, 5, forward = true))
        assertEquals(19, FocusJump.target(17, 20, 5, forward = true))
        assertEquals(0, FocusJump.target(3, 20, 5, forward = false))
        assertEquals(-1, FocusJump.target(0, 0, 5, forward = true))
    }

    @Test fun adjacent_walksSidebarPagesAcrossGroups_withoutWrapping() {
        val order = listOf("appearance", "home_layout", "network", "bluetooth", "system", "controllers", "about")
        assertEquals("network", FocusJump.adjacent(order, "home_layout", forward = true))
        assertEquals("appearance", FocusJump.adjacent(order, "home_layout", forward = false))
        assertEquals("controllers", FocusJump.adjacent(order, "system", forward = true))
        assertNull(FocusJump.adjacent(order, "about", forward = true))
        assertNull(FocusJump.adjacent(order, "appearance", forward = false))
        assertEquals("appearance", FocusJump.adjacent(order, null, forward = true))
        assertEquals("about", FocusJump.adjacent(order, "gone", forward = false))
        assertNull(FocusJump.adjacent(emptyList(), "x", forward = true))
    }

    @Test fun heldJumps_areSpacedOut() {
        assertTrue(FocusJump.repeatAllowed(0, FocusJump.REPEAT_GAP_MS))
        assertFalse(FocusJump.repeatAllowed(1000, 1000 + FocusJump.REPEAT_GAP_MS - 1))
    }
}
