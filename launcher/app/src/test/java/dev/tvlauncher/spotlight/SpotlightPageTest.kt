package dev.tvlauncher.spotlight

import android.view.KeyEvent
import dev.tvlauncher.appsettings.AdvancedToggle
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.QuickActions.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightPageTest {
    @Test fun page_isInTheHomeGroup_withinBudget() {
        assertEquals(SettingsGroup.HOME, SpotlightSection.group)
        assertEquals("spotlight", SpotlightSection.id)
        val spec = SpotlightSection.spec
        assertTrue(spec.withinBudget)
        assertTrue(spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
        assertEquals(spec.primaryRows.size, spec.primaryRows.toSet().size)
        assertTrue(spec.primaryRows.all { it.startsWith("spotlight.") })
        assertTrue(AdvancedToggle().visibleRows(spec.primaryRows.size, spec.advancedRows.size) <= PageSpec.MAX_PRIMARY + 1)
        val guard = RowGuard(spec)
        spec.primaryRows.forEach { guard.claim(it, advanced = false) }
    }

    @Test fun registryHook_placesItAtTheEndOfHome() {
        val r = SettingsRegistry.newDefault()
        r.register(SpotlightSection)
        val probe = object : dev.tvlauncher.appsettings.SectionAvailability.Probe {
            override fun appInstalled(pkg: String) = false
            override fun classPresent(name: String) = false
        }
        val home = r.groups(probe).first { it.group == SettingsGroup.HOME }
        assertEquals(SpotlightSection.id, home.sections.last().id)
        r.register(SpotlightSection) // idempotent
        assertEquals(1, r.sections().count { it.id == SpotlightSection.id })
    }

    @Test fun doublePressOptions_offerExtraOnlyWhenSet() {
        val d = SpotlightConfig.DEFAULT
        assertFalse(DoublePress.EXTRA in SpotlightPage.doubleOptions(d))
        val c = d.copy(extraKey = KeyEvent.KEYCODE_INFO)
        assertEquals(DoublePress.values().toList(), SpotlightPage.doubleOptions(c))
        assertEquals("Info", SpotlightPage.doubleLabels(c) { TriggerKeys.name(it) }.last())
    }

    @Test fun removingTheExtraKey_turnsOffItsDoublePress() {
        val c = SpotlightConfig.DEFAULT.copy(extraKey = KeyEvent.KEYCODE_INFO, doublePress = DoublePress.EXTRA)
        val r = SpotlightPage.withoutExtraKey(c)
        assertEquals(0, r.extraKey)
        assertEquals(DoublePress.OFF, r.doublePress)
        val m = SpotlightPage.withoutExtraKey(c.copy(doublePress = DoublePress.MENU))
        assertEquals(DoublePress.MENU, m.doublePress)
        assertEquals(KeyEvent.KEYCODE_GUIDE, SpotlightPage.withExtraKey(c, KeyEvent.KEYCODE_GUIDE).extraKey)
    }

    // --- Quick actions ---

    @Test fun move_leftAndRight_stopsAtTheEnds() {
        val d = SpotlightConfig.DEFAULT
        val m = QuickActionEdits.move(d, Item.SOUND, -1)!!
        assertEquals(listOf(Item.INPUTS, Item.SETTINGS, Item.NETWORK, Item.SOUND, Item.BLUETOOTH), m.order.take(5))
        assertEquals(d.order, QuickActionEdits.move(m, Item.SOUND, 1)!!.order)
        assertNull(QuickActionEdits.move(d, Item.INPUTS, -1))
        assertNull(QuickActionEdits.move(d, d.order.last(), 1))
        assertEquals(d.order.size, m.order.size)
    }

    @Test fun showHide_keepsThePlace_andItemsFollowTheConfig() {
        var c = QuickActionEdits.setShown(SpotlightConfig.DEFAULT, Item.SETTINGS, false)
        c = QuickActionEdits.setShown(c, Item.DISCOVER, true)
        c = QuickActionEdits.move(c, Item.DISCOVER, -(c.order.indexOf(Item.DISCOVER)))!!
        val items = QuickActions.items(QuickActions.Availability(emptySet()), c)
        assertEquals(Item.DISCOVER, items.first())
        assertFalse(Item.SETTINGS in items)
        assertEquals(1, c.order.indexOf(Item.INPUTS))
        val back = QuickActionEdits.setShown(c, Item.SETTINGS, true)
        assertTrue(Item.SETTINGS in QuickActions.items(QuickActions.Availability(emptySet()), back))
        assertEquals(SpotlightConfig.DEFAULT.order, QuickActionEdits.resetActions(c).order)
        assertEquals(SpotlightConfig.DEFAULT.hidden, QuickActionEdits.resetActions(c).hidden)
    }

    @Test fun availability_browserGetAppsSleep() {
        val c = SpotlightConfig.DEFAULT.copy(hidden = emptySet())
        val none = QuickActions.items(QuickActions.Availability(emptySet(), sleepTimer = false), c)
        assertFalse(Item.BROWSER in none || Item.GET_APPS in none || Item.SLEEP_TIMER in none)
        assertTrue(Item.ACCESSIBILITY in none && Item.POWER in none && Item.RESTART in none && Item.DISCOVER in none)
        // The sleep timer is the launcher's own (TV & system > Power & sleep), so it is there by default.
        assertTrue(Item.SLEEP_TIMER in QuickActions.items(QuickActions.Availability(emptySet()), c))
        val all = QuickActions.items(QuickActions.Availability(setOf(QuickActions.BROWSER_PACKAGE, QuickActions.GET_APPS_PACKAGE, QuickActions.LINK_PACKAGE)), c)
        assertEquals(Item.values().toList(), all)
    }

    @Test fun subtitles_andSummary() {
        val d = SpotlightConfig.DEFAULT
        assertEquals("1st of 10 in the row", QuickActionEdits.subtitle(d, Item.INPUTS, null))
        assertEquals("Hidden", QuickActionEdits.subtitle(d, Item.RESTART, null))
        assertEquals("10th of 10 in the row · Shown once Browser is installed", QuickActionEdits.subtitle(d, Item.BROWSER, "Shown once Browser is installed"))
        assertEquals("10 shown", QuickActionEdits.summary(d))
        assertEquals("None shown", QuickActionEdits.summary(d.copy(hidden = Item.values().toSet())))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map { QuickActionEdits.ordinal(it) })
    }

    @Test fun newRoutes_andRoomStaysOpen() {
        assertEquals(QuickActions.Route.DISCOVER, QuickActions.route(Item.DISCOVER, true))
        assertEquals(QuickActions.Route.RESTART, QuickActions.route(Item.RESTART, true))
        for (i in listOf(Item.ACCESSIBILITY, Item.POWER, Item.SLEEP_TIMER, Item.GET_APPS)) assertEquals(QuickActions.Route.SYSTEM, QuickActions.route(i, true))
        assertFalse(QuickActions.closesSpotlight(Item.ROOM))
        // Room lighting, the sleep timer and Bedtime mode change in place (OK again = next choice).
        val inPlace = setOf(Item.ROOM, Item.SLEEP_TIMER, Item.BEDTIME)
        assertTrue(inPlace.none { QuickActions.closesSpotlight(it) })
        assertTrue(Item.values().filter { it !in inPlace }.all { QuickActions.closesSpotlight(it) })
        assertEquals(Item.values().size, Item.values().map { QuickActions.iconRes(it) }.size)
    }

    // --- Search scope ---

    private val apps = listOf(
        AppEntry("com.netflix", "Netflix", "A"),
        AppEntry("com.youtube", "YouTube", "B"),
        AppEntry("com.plex", "Plex", "C")
    )
    private val pages = listOf(SettingsHit("network", "Network", 1), SettingsHit("sounds", "Sounds", 2), SettingsHit("about", "About", 3))

    @Test fun appsOnly_isTheOldSearch() {
        val s = SpotlightResults.compute("net", SearchScope.APPS, apps, pages, listOf("com.plex"))
        assertEquals(listOf(SpotlightResult.App(apps[0])), s.results)
        assertTrue(s.visible)
        assertFalse(SpotlightResults.compute("", SearchScope.APPS, apps, pages, listOf("com.plex")).visible)
    }

    @Test fun all_addsSettings_andShowsRecentForAnEmptyQuery() {
        val s = SpotlightResults.compute("net", SearchScope.ALL, apps, pages, emptyList())
        assertEquals(listOf(SpotlightResult.App(apps[0]), SpotlightResult.Setting(pages[0])), s.results)
        val r = SpotlightResults.compute("", SearchScope.ALL, apps, pages, listOf("com.plex", "gone.app", "com.netflix"))
        assertTrue(r.recent && r.visible)
        assertEquals(listOf(SpotlightResult.App(apps[2]), SpotlightResult.App(apps[0])), r.results)
        assertFalse(SpotlightResults.compute("", SearchScope.ALL, apps, pages, emptyList()).visible)
        assertEquals(listOf(SpotlightResult.Setting(pages[1])), SpotlightResults.compute("sou", SearchScope.ALL, apps, pages, emptyList()).results)
    }

    @Test fun recent_isMostRecentFirst_withoutDuplicates() {
        var r = emptyList<String>()
        for (p in listOf("a", "b", "a", "c")) r = SpotlightResults.record(r, p)
        assertEquals(listOf("c", "a", "b"), r)
        val many = (1..40).fold(emptyList<String>()) { acc, i -> SpotlightResults.record(acc, "p$i") }
        assertEquals(SpotlightResults.MAX_RECENT * 2, many.size)
    }
}
