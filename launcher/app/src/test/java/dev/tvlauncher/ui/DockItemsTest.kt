package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockItemsTest {
    @Test fun dock_hasExactlyInputsThenSettings() {
        assertEquals(listOf(DockItems.Item.INPUTS, DockItems.Item.SETTINGS), DockItems.items)
    }

    @Test fun dock_noAssistantOrAllApps() {
        val names = DockItems.items.map { it.name }
        assertFalse("ASSISTANT" in names)
        assertFalse("ALL_APPS" in names)
    }

    @Test fun firstFocus_isFirstItem() {
        assertEquals(0, DockItems.firstFocusIndex)
    }

    @Test fun onlySettings_hasLongPress() {
        assertTrue(DockItems.Item.SETTINGS.hasLongPress)
        assertFalse(DockItems.Item.INPUTS.hasLongPress)
    }

    @Test fun reservedSlot_leavesRoomForFutureBrowser() {
        assertTrue(DockItems.MAX_ITEMS >= DockItems.items.size + 1)
    }
}
