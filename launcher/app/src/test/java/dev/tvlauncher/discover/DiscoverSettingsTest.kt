package dev.tvlauncher.discover

import dev.tvlauncher.nowplaying.NowPlayingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverSettingsTest {
    @Test fun defaults() {
        val d = DiscoverSettings()
        assertTrue(d.showGetMoreAppsTile)
        assertEquals(null, d.region)
        assertTrue(d.hiddenCatalogIds.isEmpty())
        val n = NowPlayingSettings()
        assertTrue(n.nowPlayingEnabled) // on by default, hidden until access exists
        assertFalse(n.showHome(accessGranted = false))
        assertTrue(n.showOnHome)
        assertTrue(n.showOnAmbient)
    }

    @Test fun discover_roundTrip() {
        val d = DiscoverSettings(region = "IN", showGetMoreAppsTile = false, hiddenCatalogIds = setOf("netflix", "tubi"))
        assertEquals(d, DiscoverSettings.fromJson(d.toJson()))
    }

    @Test fun discover_tolerant() {
        assertEquals(DiscoverSettings(), DiscoverSettings.fromJson(null))
        assertEquals(DiscoverSettings(), DiscoverSettings.fromJson("{not json"))
        assertEquals(DiscoverSettings(), DiscoverSettings.fromJson("[]"))
        val d = DiscoverSettings.fromJson("""{"region":"zz-top","showGetMoreAppsTile":"yes","hidden":[1,"ok id","",null,"tubi"]}""")
        assertEquals(null, d.region)
        assertTrue(d.showGetMoreAppsTile)
        assertEquals(setOf("tubi"), d.hiddenCatalogIds)
        assertEquals("IN", DiscoverSettings.fromJson("""{"region":"in"}""").region)
    }

    @Test fun nowPlaying_roundTrip_andTolerant() {
        val n = NowPlayingSettings(nowPlayingEnabled = true, showOnHome = false, showOnAmbient = false)
        assertEquals(n, NowPlayingSettings.fromJson(n.toJson()))
        assertEquals(NowPlayingSettings(), NowPlayingSettings.fromJson("garbage"))
        assertEquals(NowPlayingSettings(), NowPlayingSettings.fromJson("""{"enabled":"true"}"""))
    }

    @Test fun nowPlaying_effective_requiresAccess() {
        val on = NowPlayingSettings(nowPlayingEnabled = true)
        assertTrue(on.effective(accessGranted = true).nowPlayingEnabled)
        assertFalse(on.effective(accessGranted = false).nowPlayingEnabled)
        assertFalse(on.showHome(accessGranted = false))
        assertTrue(on.showHome(accessGranted = true))
        assertFalse(on.copy(showOnHome = false).showHome(true))
        assertFalse(on.copy(showOnAmbient = false).showAmbient(true))
    }

    @Test fun rows_accessCard_visibleOnlyWhenEnabledWithoutAccess() {
        val off = DiscoverSettingsRows.rows(DiscoverSettings(), NowPlayingSettings(nowPlayingEnabled = false), false, "IN")
        assertFalse(off.first { it.key == "nowPlayingAccess" }.visible)
        assertFalse(off.first { it.key == "nowPlayingShowOnHome" }.visible)
        val noAccess = DiscoverSettingsRows.rows(DiscoverSettings(), NowPlayingSettings(nowPlayingEnabled = true), false, "IN")
        val card = noAccess.first { it.key == "nowPlayingAccess" }
        assertTrue(card.visible)
        assertEquals(DiscoverSettingsRows.Kind.CARD, card.kind)
        assertTrue(!card.hint!!.contains("adb") && !card.hint!!.contains("allow_listener"))
        val ok = DiscoverSettingsRows.rows(DiscoverSettings(), NowPlayingSettings(nowPlayingEnabled = true), true, "IN")
        assertFalse(ok.first { it.key == "nowPlayingAccess" }.visible)
        assertTrue(ok.first { it.key == "nowPlayingShowOnHome" }.visible)
    }

    @Test fun rows_regionValue_showsAutoWithDetected() {
        val r = DiscoverSettingsRows.rows(DiscoverSettings(), NowPlayingSettings(), false, "IN").first { it.key == "region" }
        assertEquals("Automatic (IN)", r.value)
        val r2 = DiscoverSettingsRows.rows(DiscoverSettings(region = "US"), NowPlayingSettings(), false, "IN").first { it.key == "region" }
        assertEquals("US", r2.value)
    }

    @Test fun toggles_andRegionStep() {
        var d = DiscoverSettings()
        var n = NowPlayingSettings()
        d = DiscoverSettingsRows.toggle(d, "showGetMoreAppsTile")
        assertFalse(d.showGetMoreAppsTile)
        n = DiscoverSettingsRows.toggleNowPlaying(n, "nowPlayingEnabled")
        assertFalse(n.nowPlayingEnabled) // on by default: the first toggle turns it off
        n = DiscoverSettingsRows.toggleNowPlaying(n, "nowPlayingShowOnAmbient")
        assertFalse(n.showOnAmbient)
        val stepped = DiscoverSettingsRows.stepRegion(DiscoverSettings(), 1)
        assertEquals(DiscoverSettingsRows.REGIONS[1], stepped.region)
        assertEquals(null, DiscoverSettingsRows.stepRegion(stepped, -1).region)
        assertEquals(null, DiscoverSettingsRows.stepRegion(DiscoverSettings(), -1).region)
    }

    @Test fun hideAndRestore() {
        val d = DiscoverSettingsRows.hide(DiscoverSettings(), "netflix")
        assertEquals(setOf("netflix"), d.hiddenCatalogIds)
        assertTrue(DiscoverSettingsRows.restoreHidden(d).hiddenCatalogIds.isEmpty())
        val row = DiscoverSettingsRows.rows(d, NowPlayingSettings(), false, null).first { it.key == "restoreHidden" }
        assertTrue(row.visible)
        assertEquals("1 hidden", row.value)
    }
}
