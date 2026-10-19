package dev.tvlauncher.spotlight

import android.view.KeyEvent
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.QuickActions.Item
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightConfigTest {
    private val original = listOf(
        Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.BLUETOOTH, Item.SOUND,
        Item.DEVICES, Item.AMBIENT, Item.ROOM, Item.INSIGHTS, Item.BROWSER
    )

    @Test fun defaults_reproduceTheOldFixedRow() {
        val c = SpotlightConfig.DEFAULT
        assertEquals(original, c.shown)
        assertEquals(Item.values().toList(), c.order)
        assertTrue(c.menuKey && c.searchKey)
        assertEquals(0, c.extraKey)
        assertEquals(DoublePress.OFF, c.doublePress)
        assertEquals(SearchScope.APPS, c.scope)
        assertTrue(c.showHints)
        assertTrue(c.closeAfterAction)
        // The row itself is unchanged too: Browser only when installed.
        assertEquals(original - Item.BROWSER, QuickActions.items(QuickActions.Availability(emptySet()), c))
    }

    @Test fun newActions_areHiddenByDefault() {
        for (i in listOf(Item.ACCESSIBILITY, Item.POWER, Item.SLEEP_TIMER, Item.RESTART, Item.GET_APPS, Item.DISCOVER)) {
            assertTrue(i in SpotlightConfig.DEFAULT.hidden)
        }
    }

    @Test fun missingOrBrokenJson_givesDefaults() {
        for (j in listOf(null, "", "  ", "{oops", "[1,2]", "42")) assertEquals(SpotlightConfig.DEFAULT, SpotlightConfig.fromJson(j))
        assertEquals(SpotlightConfig.DEFAULT, SpotlightConfig.fromJson("{}"))
    }

    @Test fun roundTrip() {
        val c = SpotlightConfig(
            order = Item.values().reversed(),
            hidden = setOf(Item.SOUND, Item.INSIGHTS),
            menuKey = false,
            searchKey = true,
            extraKey = KeyEvent.KEYCODE_BOOKMARK,
            doublePress = DoublePress.EXTRA,
            scope = SearchScope.ALL,
            showHints = false,
            closeAfterAction = false
        )
        assertEquals(c, SpotlightConfig.fromJson(c.toJson()))
        assertEquals(SpotlightConfig.VERSION, JSONObject(c.toJson()).getInt("v"))
    }

    @Test fun migration_dropsUnknown_dedupes_andAppendsNewActions() {
        val json = JSONObject()
            .put("order", JSONArray(listOf("SOUND", "GONE_ITEM", "INPUTS", "SOUND", "SETTINGS")))
            .put("hidden", JSONArray(listOf("INPUTS", "NOPE")))
            .toString()
        val c = SpotlightConfig.fromJson(json)
        assertEquals(listOf(Item.SOUND, Item.INPUTS, Item.SETTINGS), c.order.take(3))
        assertEquals(Item.values().size, c.order.size)
        assertEquals(Item.values().toSet(), c.order.toSet())
        assertTrue(Item.INPUTS in c.hidden)
        // Appended old actions show; appended new ones stay hidden.
        assertFalse(Item.NETWORK in c.hidden)
        assertTrue(Item.RESTART in c.hidden)
        assertEquals(listOf(Item.SOUND, Item.SETTINGS), c.shown.take(2))
    }

    @Test fun reservedOrSwitchedExtraKey_isCleared_andItsDoublePressToo() {
        for (k in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MENU, -5)) {
            val c = SpotlightConfig.fromJson(JSONObject().put("extraKey", k).put("doublePress", "EXTRA").toString())
            assertEquals(0, c.extraKey)
            assertEquals(DoublePress.OFF, c.doublePress)
        }
    }

    @Test fun wrongTypes_fallBackPerField() {
        val c = SpotlightConfig.fromJson(JSONObject().put("menuKey", "yes").put("scope", "EVERYTHING").put("showHints", false).toString())
        assertTrue(c.menuKey)
        assertEquals(SearchScope.APPS, c.scope)
        assertFalse(c.showHints)
    }
}
