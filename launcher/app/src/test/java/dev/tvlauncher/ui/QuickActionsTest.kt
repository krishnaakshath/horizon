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
            Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.BLUETOOTH, Item.SOUND,
            Item.DEVICES, Item.AMBIENT, Item.ROOM, Item.INSIGHTS
        )
        assertEquals(expected, QuickActions.items(emptySet()))
        assertEquals(expected, QuickActions.items(setOf("a", "b")))
        assertEquals(expected + Item.BROWSER, QuickActions.items(setOf(QuickActions.BROWSER_PACKAGE)))
    }

    @Test fun room_isAlwaysThere_andCyclesOnHome() {
        for (set in listOf(emptySet(), setOf(QuickActions.BROWSER_PACKAGE))) {
            val items = QuickActions.items(set)
            assertEquals(items.indexOf(Item.AMBIENT) + 1, items.indexOf(Item.ROOM))
        }
        assertEquals(QuickActions.Route.ROOM_CYCLE, QuickActions.route(Item.ROOM, bluetoothPresent = true))
        assertEquals("Room lighting", Item.ROOM.title)
    }

    @Test fun onlySettings_hasLongPress() {
        assertEquals(listOf(Item.SETTINGS), Item.values().filter { it.hasLongPress })
    }

    @Test fun browserPackage_isOurs() {
        assertEquals("dev.tvlauncher.browser", QuickActions.BROWSER_PACKAGE)
    }

    @Test fun ambient_and_devices_always() {
        for (set in listOf(emptySet(), setOf(QuickActions.BROWSER_PACKAGE))) {
            val items = QuickActions.items(set)
            assertTrue(Item.AMBIENT in items)
            assertTrue(Item.DEVICES in items)
            assertTrue(items.indexOf(Item.AMBIENT) < items.indexOf(Item.INSIGHTS))
        }
    }

    @Test fun route_panelsDirect_withSystemFallback() {
        assertEquals(QuickActions.Route.WIFI_PANEL, QuickActions.route(Item.NETWORK, bluetoothPresent = true))
        assertEquals(QuickActions.Route.BLUETOOTH_PANEL, QuickActions.route(Item.BLUETOOTH, bluetoothPresent = true))
        assertEquals(QuickActions.Route.SYSTEM, QuickActions.route(Item.BLUETOOTH, bluetoothPresent = false))
        assertEquals(QuickActions.Route.DEVICES_PANEL, QuickActions.route(Item.DEVICES, bluetoothPresent = false))
        assertEquals(QuickActions.Route.AMBIENT, QuickActions.route(Item.AMBIENT, bluetoothPresent = false))
        assertEquals(QuickActions.Route.INPUTS_PANEL, QuickActions.route(Item.INPUTS, bluetoothPresent = false))
        for (i in listOf(Item.SETTINGS, Item.SOUND, Item.INSIGHTS, Item.BROWSER)) {
            assertEquals(QuickActions.Route.SYSTEM, QuickActions.route(i, bluetoothPresent = true))
        }
    }

    @Test fun systemRoute_alwaysLandsInTheLaunchersOwnPages() {
        assertEquals(null, QuickActions.launcherSection(Item.SETTINGS))
        assertEquals("system", QuickActions.launcherSection(Item.SOUND))
        assertEquals("bluetooth", QuickActions.launcherSection(Item.BLUETOOTH))
        assertEquals("network", QuickActions.launcherSection(Item.NETWORK))
        assertEquals("devices", QuickActions.launcherSection(Item.DEVICES))
        assertEquals("accessibility", QuickActions.launcherSection(Item.ACCESSIBILITY))
        assertEquals("system", QuickActions.launcherSection(Item.POWER))
        assertEquals("system", QuickActions.launcherSection(Item.SLEEP_TIMER)) // the launcher's own timer
    }

    @Test fun titles_forNewItems() {
        assertEquals("Devices", Item.DEVICES.title)
        assertEquals("Ambient", Item.AMBIENT.title)
    }
}
