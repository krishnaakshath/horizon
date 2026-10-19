package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusRulesTest {
    @Test fun removal_midList_focusesRightNeighbor() {
        assertEquals(2, FocusRules.nextFocusIndexAfterRemoval(2, 4))
    }

    @Test fun removal_lastItem_focusesLeftNeighbor() {
        assertEquals(3, FocusRules.nextFocusIndexAfterRemoval(4, 4))
    }

    @Test fun removal_onlyItem_returnsNull() {
        assertNull(FocusRules.nextFocusIndexAfterRemoval(0, 0))
    }

    @Test fun removal_outOfRangeIndex_clampsOrNull() {
        assertEquals(0, FocusRules.nextFocusIndexAfterRemoval(-3, 5))
        assertEquals(4, FocusRules.nextFocusIndexAfterRemoval(99, 5))
        assertNull(FocusRules.nextFocusIndexAfterRemoval(-1, 0))
        assertNull(FocusRules.nextFocusIndexAfterRemoval(7, 0))
    }
}
