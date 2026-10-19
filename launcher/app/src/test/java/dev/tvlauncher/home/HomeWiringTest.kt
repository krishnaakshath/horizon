package dev.tvlauncher.home

import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.discover.DiscoverSettingsRows
import dev.tvlauncher.nowplaying.NowPlayingSettings
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomSettings
import dev.tvlauncher.room.RoomSettingsRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWiringTest {
    // --- Now Playing on Home -------------------------------------------------------------------

    @Test fun nowPlaying_runsOnlyWhenEnabledShownAndGranted() {
        val on = NowPlayingSettings(nowPlayingEnabled = true, showOnHome = true)
        assertTrue(NowPlayingHome.shouldRun(on, accessGranted = true))
        assertFalse(NowPlayingHome.shouldRun(on, accessGranted = false))
        // Mini player off: the art backdrop still needs the tracker; both off: nothing runs.
        assertTrue(NowPlayingHome.shouldRun(on.copy(showOnHome = false), accessGranted = true))
        assertFalse(NowPlayingHome.shouldRun(on.copy(showOnHome = false, backdropFollows = false), accessGranted = true))
        assertTrue(NowPlayingHome.shouldRun(on.copy(backdropFollows = false), accessGranted = true))
        assertFalse(NowPlayingHome.shouldRun(on.copy(nowPlayingEnabled = false), accessGranted = true))
        assertTrue(NowPlayingHome.shouldRun(NowPlayingSettings(), accessGranted = true)) // on by default
        assertFalse(NowPlayingHome.shouldRun(NowPlayingSettings(), accessGranted = false)) // hidden until access exists
    }

    @Test fun miniPlayer_isTheUpTargetOnlyWhileShown() {
        assertTrue(NowPlayingHome.upLinkWanted(cardShown = true, focusOnTile = true))
        assertFalse(NowPlayingHome.upLinkWanted(cardShown = false, focusOnTile = true))
        assertFalse(NowPlayingHome.upLinkWanted(cardShown = true, focusOnTile = false))
    }

    @Test fun accessCard_onlyWhileEnabledButNotGranted() {
        assertTrue(WaveTwoSections.accessCardShown(nowPlayingEnabled = true, granted = false))
        assertFalse(WaveTwoSections.accessCardShown(nowPlayingEnabled = true, granted = true))
        assertFalse(WaveTwoSections.accessCardShown(nowPlayingEnabled = false, granted = false))
    }

    // --- Home screen page ----------------------------------------------------------------------

    @Test fun homeScreenPage_rowsPassTheGuard() {
        for (hidden in listOf(0, 3)) {
            val guard = RowGuard(WaveTwoSections.HOME_EXTRAS_SPEC)
            val rows = WaveTwoSections.homeExtrasRows(hidden)
            rows.forEach { (k, adv) -> guard.claim(k, adv) }
            assertEquals(hidden > 0, rows.any { it.first == "home.restoreHidden" })
        }
        assertTrue(WaveTwoSections.HOME_EXTRAS_SPEC.withinBudget)
    }

    @Test fun regionLabels_matchTheStepper() {
        val labels = WaveTwoSections.regionLabels("in")
        assertEquals(DiscoverSettingsRows.REGIONS.size, labels.size)
        assertEquals("Automatic (IN)", labels[0])
        assertEquals("Automatic", WaveTwoSections.regionLabels(null)[0])
        assertEquals("US", labels[DiscoverSettingsRows.REGIONS.indexOf("US")])
    }

    // --- Room lighting page --------------------------------------------------------------------

    @Test fun roomPage_isRegisteredInHome_withinBudget() {
        val r = SettingsRegistry.newDefault()
        WaveTwoSections.registerAll(r)
        val room = r.sections().first { it.id == RoomPage.ID }
        assertEquals(SettingsGroup.HOME, room.group)
        assertTrue(RoomPage.SPEC.primaryRows.size <= PageSpec.MAX_PRIMARY)
        val keys = RoomPage.SPEC.primaryRows + RoomPage.SPEC.advancedRows
        assertEquals(keys.size, keys.toSet().size)
        // Every page row maps to a model row.
        for (k in keys) assertTrue(k, RoomSettingsRows.row(RoomPage.modelKey(k)) != null || RoomPage.modelKey(k) == "location")
        val guard = RowGuard(RoomPage.SPEC)
        RoomPage.PRIMARY.forEach { guard.claim(it, false) }
        RoomPage.ADVANCED.forEach { guard.claim(it, true) }
    }

    @Test fun roomPage_choiceIndexRoundTrips() {
        for (c in RoomChoice.values()) {
            val s = RoomSettings(choice = c)
            assertEquals(c, RoomPage.choices()[RoomPage.choiceIndex(s)])
        }
        assertEquals(RoomPage.choices().size, RoomPage.choiceLabels().size)
    }

    @Test fun roomPage_locationPicksCitiesAndClears() {
        val none = RoomSettings()
        assertEquals(0, RoomPage.locationIndex(none))
        assertEquals(1 + RoomSettingsRows.CITIES.size, RoomPage.locationLabels(none).size)
        for (i in 1..RoomSettingsRows.CITIES.size) {
            val s = RoomPage.applyLocation(none, i)
            assertEquals(i, RoomPage.locationIndex(s))
        }
        val cleared = RoomPage.applyLocation(RoomPage.applyLocation(none, 1), 0)
        assertEquals(null, cleared.latitudeDeg)
        assertEquals(null, cleared.longitudeDeg)
    }

    @Test fun roomPage_customLocationGetsItsOwnEntry() {
        val custom = RoomSettings(latitudeDeg = 47.5, longitudeDeg = 19.04)
        val labels = RoomPage.locationLabels(custom)
        assertEquals(2 + RoomSettingsRows.CITIES.size, labels.size)
        assertEquals(labels.size - 1, RoomPage.locationIndex(custom))
        assertEquals(custom, RoomPage.applyLocation(custom, labels.size - 1)) // picking it keeps it
    }

    @Test fun roomPage_transitionSnapsToNearestPreset() {
        val opts = RoomPage.transitionOptions().map { it.value.toFloat() }
        for ((i, v) in opts.withIndex()) assertEquals(i, RoomPage.transitionIndex(RoomSettings(transitionSeconds = v)))
        assertEquals(opts.indexOf(2f), RoomPage.transitionIndex(RoomSettings(transitionSeconds = 5f)))
    }

    @Test fun roomPage_scheduleSteps15Minutes() {
        assertEquals(0, RoomPage.slot(0))
        assertEquals(95, RoomPage.MAX_SLOT)
        assertEquals(1425, RoomPage.minuteOf(RoomPage.MAX_SLOT))
        assertEquals(18 * 60, RoomPage.minuteOf(RoomPage.slot(18 * 60 + 7)))
        assertEquals(0, RoomPage.minuteOf(-3))
    }
}
