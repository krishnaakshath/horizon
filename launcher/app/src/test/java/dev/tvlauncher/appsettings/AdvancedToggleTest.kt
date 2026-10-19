package dev.tvlauncher.appsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedToggleTest {
    @Test
    fun collapsedByDefault() {
        assertFalse(AdvancedToggle().expanded)
    }

    @Test
    fun opening_movesFocusToTheFirstAdvancedRow_closing_backToTheToggle() {
        val open = AdvancedToggle().toggle()
        assertTrue(open.expanded)
        assertEquals(5, open.focusAfterToggle(toggleIndex = 4, advancedCount = 3))
        val closed = open.toggle()
        assertEquals(4, closed.focusAfterToggle(toggleIndex = 4, advancedCount = 3))
    }

    @Test
    fun opening_anEmptyBlock_keepsFocus() {
        assertEquals(2, AdvancedToggle(expanded = true).focusAfterToggle(2, 0))
    }

    @Test
    fun visibleRows_countsTheToggleOnlyWhenThereIsSomethingToHide() {
        assertEquals(4, AdvancedToggle().visibleRows(primary = 4, advanced = 0))
        assertEquals(5, AdvancedToggle().visibleRows(primary = 4, advanced = 3))
        assertEquals(8, AdvancedToggle(true).visibleRows(primary = 4, advanced = 3))
    }
}
