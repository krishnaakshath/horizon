package dev.tvlauncher.appsettings

import dev.tvlauncher.appsettings.SidebarEntry.Header
import dev.tvlauncher.appsettings.SidebarEntry.Page
import dev.tvlauncher.appsettings.SettingsGroup.APPS
import dev.tvlauncher.appsettings.SettingsGroup.CONNECTIONS
import dev.tvlauncher.appsettings.SettingsGroup.HOME
import dev.tvlauncher.appsettings.SettingsGroup.SOUND
import dev.tvlauncher.appsettings.SettingsGroup.SYSTEM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarStateTest {
    private val groups = listOf(
        HOME to listOf("appearance", "home_layout"),
        CONNECTIONS to listOf("network", "bluetooth"),
        SOUND to listOf("sounds"),
        APPS to listOf("browser", "getapps", "helper", "insights", "addons"),
        SYSTEM to listOf("system", "backup", "about")
    )

    @Test
    fun initial_expandsOnlyTheCurrentGroup() {
        val s = SidebarState.initial(groups, "network")
        assertEquals(
            listOf(
                Header(HOME, false), Header(CONNECTIONS, true), Page("network", CONNECTIONS), Page("bluetooth", CONNECTIONS),
                Header(SOUND, false), Header(APPS, false), Header(SYSTEM, false)
            ),
            s.entries
        )
    }

    @Test
    fun collapsedGroups_areSkipped_byDown() {
        val s = SidebarState.initial(groups, "appearance")
        // Header HOME, appearance, home_layout, Header CONNECTIONS ...
        val fromHomeLayout = s.move(s.indexOfPage("home_layout"), +1)
        assertEquals(Header(CONNECTIONS, false), s.entries[fromHomeLayout])
        assertEquals(Header(SOUND, false), s.entries[s.move(fromHomeLayout, +1)])
    }

    @Test
    fun move_clampsAtBothEnds() {
        val s = SidebarState.initial(groups, "appearance")
        assertEquals(0, s.move(0, -1))
        assertEquals(s.entries.lastIndex, s.move(s.entries.lastIndex, +1))
    }

    @Test
    fun toggle_isAnAccordion_andKeepsFocusOnTheHeader() {
        val s = SidebarState.initial(groups, "appearance").toggle(APPS)
        assertEquals(APPS, s.expanded)
        assertTrue(s.entries.none { it is Page && it.group == HOME })
        val i = s.indexOfHeader(APPS)
        assertEquals(Header(APPS, true), s.entries[i])
        assertEquals(Page("browser", APPS), s.entries[i + 1])
        val closed = s.toggle(APPS)
        assertNull(closed.expanded)
        assertEquals(5, closed.entries.size)
    }

    @Test
    fun atMostNineEntries_whenAnyOneGroupIsOpen_exceptTheBiggest() {
        for ((g, ids) in groups) {
            val n = SidebarState.initial(groups, ids.first()).entries.size
            assertTrue("$g shows $n", n <= 10)
            if (g != APPS) assertTrue("$g shows $n", n <= 9)
        }
    }

    @Test
    fun select_setsCurrentAndExpandsItsGroup() {
        val s = SidebarState.initial(groups, "appearance").toggle(SYSTEM).select("about")
        assertEquals("about", s.current)
        assertEquals(SYSTEM, s.expanded)
    }

    @Test
    fun reveal_reopensTheCurrentGroup_afterTheUserCollapsedIt() {
        val s = SidebarState.initial(groups, "sounds").toggle(SOUND)
        assertEquals(-1, s.indexOfPage("sounds"))
        val r = s.reveal()
        assertEquals(SOUND, r.expanded)
        assertEquals(Page("sounds", SOUND), r.entries[r.indexOfPage("sounds")])
    }

    @Test
    fun focusIndexAfterToggle_isTheHeaderIndexInTheNewList() {
        val s = SidebarState.initial(groups, "appearance")
        val before = s.indexOfHeader(SYSTEM)
        assertEquals(6, before)
        val after = s.toggle(SYSTEM)
        assertEquals(4, after.indexOfHeader(SYSTEM))
    }

    @Test
    fun startPage_prefersSavedThenRequestedThenLast_andSkipsUnavailable() {
        val avail = listOf("appearance", "sounds", "about")
        assertEquals("about", SidebarState.startPage(saved = "about", requested = "sounds", last = "appearance", available = avail))
        assertEquals("sounds", SidebarState.startPage(saved = null, requested = "sounds", last = "about", available = avail))
        assertEquals("about", SidebarState.startPage(saved = "gone", requested = null, last = "about", available = avail))
        assertEquals("appearance", SidebarState.startPage(saved = null, requested = "browser", last = "helper", available = avail))
        assertNull(SidebarState.startPage(null, null, null, emptyList()))
    }

    @Test
    fun unknownCurrent_leavesEverythingCollapsed() {
        val s = SidebarState.initial(groups, "nope")
        assertNull(s.expanded)
        assertTrue(s.entries.all { it is Header })
    }

    @Test
    fun breadcrumb_neverRepeatsTheTitle() {
        assertEquals("Home", Breadcrumb.of("Home", "Appearance", emptyList()))
        assertEquals("Home › Home layout", Breadcrumb.of("Home", "Home layout", listOf("Favorites")))
        assertEquals("Sound › Sounds › Sound lab", Breadcrumb.of("Sound", "Sounds", listOf("Sound lab", "Play")))
    }

    @Test
    fun regroup_keepsAGroupTheUserCollapsed_collapsed() {
        val collapsed = SidebarState.initial(groups, "sounds").toggle(SOUND)
        val more = groups.map { (g, ids) -> if (g == APPS) g to ids + "weather" else g to ids }
        val r = SidebarState.regroup(collapsed, more, "sounds")
        assertNull(r.expanded)
    }

    @Test
    fun regroup_keepsTheOpenGroup_evenIfItIsNotTheCurrentPagesGroup() {
        val browsing = SidebarState.initial(groups, "appearance").toggle(SYSTEM)
        val fewer = groups.map { (g, ids) -> if (g == APPS) g to ids - "browser" else g to ids }
        assertEquals(SYSTEM, SidebarState.regroup(browsing, fewer, "appearance").expanded)
    }

    @Test
    fun regroup_opensTheCurrentGroup_onlyWhenTheOpenGroupDisappeared() {
        val open = SidebarState.initial(groups, "appearance").toggle(SOUND)
        val noSound = groups.filter { it.first != SOUND }
        assertEquals(HOME, SidebarState.regroup(open, noSound, "appearance").expanded)
    }

    @Test
    fun indexOf_mapsOldEntriesIntoTheNewList_headersStayHeaders() {
        val old = SidebarState.initial(groups, "browser")
        val header = Header(SYSTEM, false)
        val fewer = groups.map { (g, ids) -> if (g == APPS) g to ids - "helper" else g to ids }
        val now = SidebarState.regroup(old, fewer, "browser")
        assertEquals(now.indexOfHeader(SYSTEM), now.indexOf(header))
        assertEquals(now.indexOfHeader(APPS), now.indexOf(Page("helper", APPS)))
        assertEquals(now.indexOfPage("insights"), now.indexOf(Page("insights", APPS)))
    }

    @Test
    fun aSixEntryAppsGroup_isFullyReachable_byDown() {
        val big = groups.map { (g, ids) -> if (g == APPS) g to ids + "weather" else g to ids }
        val s = SidebarState.initial(big, "browser")
        assertEquals(11, s.entries.size)
        var i = 0
        val seen = HashSet<Int>()
        repeat(20) { seen += i; i = s.move(i, +1) }
        assertEquals((0 until 11).toSet(), seen)
    }
}
