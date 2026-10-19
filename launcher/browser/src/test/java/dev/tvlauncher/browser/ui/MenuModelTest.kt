package dev.tvlauncher.browser.ui

import dev.tvlauncher.browser.ui.Menus.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuModelTest {
    @Test fun clearCard_defaultFocusIsCancel() {
        val kinds = Menus.CLEAR_DATA.entries.map { it.kind }
        assertEquals(Kind.CANCEL, kinds[MenuModel.defaultFocusIndex(kinds)])
        assertEquals(0, MenuModel.defaultFocusIndex(kinds))
        val all = Menus.CLEAR_EVERYTHING.entries.map { it.kind }
        assertEquals(0, MenuModel.defaultFocusIndex(all))
    }

    @Test fun nonDestructiveCard_defaultFocusIsFirst() {
        assertEquals(0, MenuModel.defaultFocusIndex(listOf(Kind.NORMAL, Kind.NORMAL)))
        assertEquals(0, MenuModel.defaultFocusIndex(Menus.LINK.entries.map { it.kind }))
    }

    @Test fun destructiveWithoutLeadingCancel_stillFocusesCancel() {
        assertEquals(2, MenuModel.defaultFocusIndex(listOf(Kind.DESTRUCTIVE, Kind.NORMAL, Kind.CANCEL)))
    }

    @Test fun everyDestructiveAction_hasCancelFirst() {
        Menus.ALL.forEach { def ->
            val kinds = def.entries.map { it.kind }
            if (Kind.DESTRUCTIVE in kinds) {
                assertEquals("${def.id}: Cancel must be first", Kind.CANCEL, kinds.first())
                assertEquals("${def.id}: default focus", 0, MenuModel.defaultFocusIndex(kinds))
            }
        }
        assertTrue(Menus.ALL.count { d -> d.entries.any { it.kind == Kind.DESTRUCTIVE } } >= 5)
    }

    @Test fun emptyCard_focusZero() = assertEquals(0, MenuModel.defaultFocusIndex(emptyList()))
}
