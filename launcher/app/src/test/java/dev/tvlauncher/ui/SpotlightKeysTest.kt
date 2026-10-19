package dev.tvlauncher.ui

import android.view.KeyEvent
import dev.tvlauncher.ui.SpotlightKeys.TileDown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightKeysTest {
    @Test fun menuAndSearch_toggle() {
        assertTrue(SpotlightKeys.isToggleKey(KeyEvent.KEYCODE_MENU))
        assertTrue(SpotlightKeys.isToggleKey(KeyEvent.KEYCODE_SEARCH))
        assertFalse(SpotlightKeys.isToggleKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(SpotlightKeys.isToggleKey(KeyEvent.KEYCODE_BACK))
    }

    @Test fun downOnTile_prefersVisibleStrip() {
        assertEquals(TileDown.STRIP, SpotlightKeys.onTileDown(stripVisible = true))
        assertEquals(TileDown.SPOTLIGHT, SpotlightKeys.onTileDown(stripVisible = false))
    }

    @Test fun slideDurations_withinBudget() {
        assertTrue(SpotlightKeys.openMs(full = true) <= 250)
        assertTrue(SpotlightKeys.openMs(full = false) <= 180)
    }

    @Test fun intentAction() {
        assertEquals("dev.tvlauncher.action.SPOTLIGHT", SpotlightKeys.ACTION)
    }

    @Test fun request_beforeFirstLoad_isDeferred() {
        assertEquals(SpotlightKeys.Request.DEFER, SpotlightKeys.onRequest(appsLoaded = false))
        assertEquals(SpotlightKeys.Request.OPEN_NOW, SpotlightKeys.onRequest(appsLoaded = true))
    }
}
