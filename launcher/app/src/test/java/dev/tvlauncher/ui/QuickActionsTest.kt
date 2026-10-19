package dev.tvlauncher.ui

import dev.tvlauncher.ui.QuickActions.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {
    @Test fun browser_hidden_whenNotInstalled() {
        val items = QuickActions.items(setOf("com.netflix.ninja"))
        assertFalse(Item.BROWSER in items)
    }

    @Test fun browser_present_whenInstalled() {
        val items = QuickActions.items(setOf(QuickActions.BROWSER_PACKAGE, "com.netflix.ninja"))
        assertTrue(Item.BROWSER in items)
        assertEquals(Item.BROWSER, items.last())
    }

    @Test fun order_isStable() {
        val expected = listOf(
            Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.BLUETOOTH, Item.SOUND, Item.INSIGHTS
        )
        assertEquals(expected, QuickActions.items(emptySet()))
        assertEquals(expected, QuickActions.items(setOf("a", "b")))
        assertEquals(expected + Item.BROWSER, QuickActions.items(setOf(QuickActions.BROWSER_PACKAGE)))
    }

    @Test fun onlySettings_hasLongPress() {
        assertEquals(listOf(Item.SETTINGS), Item.values().filter { it.hasLongPress })
    }

    @Test fun browserPackage_isOurs() {
        assertEquals("dev.tvlauncher.browser", QuickActions.BROWSER_PACKAGE)
    }
}
