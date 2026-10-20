package dev.tvlauncher.discover

import dev.tvlauncher.discover.DiscoverActions.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DiscoverActionsTest {
    private val base = PopularApps.byPackage("com.netflix.ninja")!!

    @Test fun installed_isOpen() {
        assertEquals(Action.OPEN, DiscoverActions.decide(base, setOf("com.netflix.ninja"), getAppsInstalled = false))
    }

    @Test fun play_notInstalled_isPlayStore() {
        assertEquals(Action.OPEN_PLAY_STORE, DiscoverActions.decide(base, emptySet(), false))
        assertEquals(Action.OPEN_PLAY_STORE, DiscoverActions.decide(base, emptySet(), true))
    }

    @Test fun fdroid_prefersPlayWhenListed_elseGetApps_elseNone() {
        // Get Apps ignores deep-link extras today, so a Play listing is the more direct path.
        val f = base.copy(packageName = "org.xbmc.kodi", source = Source.Fdroid("org.xbmc.kodi"), onPlay = true)
        assertEquals(Action.OPEN_PLAY_STORE, DiscoverActions.decide(f, emptySet(), true))
        assertEquals(Action.OPEN_PLAY_STORE, DiscoverActions.decide(f, emptySet(), false))
        assertEquals(Action.OPEN_GET_APPS, DiscoverActions.decide(f.copy(onPlay = false), emptySet(), true))
        assertEquals(Action.NONE, DiscoverActions.decide(f.copy(onPlay = false), emptySet(), false))
    }

    @Test fun github_onlyViaGetApps() {
        val g = base.copy(packageName = "org.example.tv", source = Source.Github("owner/repo"), onPlay = false)
        assertEquals(Action.OPEN_GET_APPS, DiscoverActions.decide(g, emptySet(), true))
        assertEquals(Action.NONE, DiscoverActions.decide(g, emptySet(), false))
    }

    @Test fun invalidPackage_isNone() {
        assertEquals(Action.NONE, DiscoverActions.decide(base.copy(packageName = "x&y"), emptySet(), false))
    }

    @Test fun labels() {
        assertEquals("Open", DiscoverActions.label(Action.OPEN))
        assertEquals("Install", DiscoverActions.label(Action.OPEN_PLAY_STORE))
        assertEquals("Open H02 Store", DiscoverActions.label(Action.OPEN_GET_APPS))
        assertEquals("Not available", DiscoverActions.label(Action.NONE))
    }

    @Test fun getAppsLink_validatesSources() {
        val f = GetAppsLink.forSource(Source.Fdroid("org.xbmc.kodi"))!!
        assertEquals("fdroid", f.source)
        assertEquals("org.xbmc.kodi", f.value)
        assertEquals("owner/repo-1", GetAppsLink.forSource(Source.Github("owner/repo-1"))!!.value)
        assertNull(GetAppsLink.forSource(Source.Github("owner/../x")))
        assertNull(GetAppsLink.forSource(Source.Github("a/b/c")))
        assertNull(GetAppsLink.forSource(Source.Fdroid("bad id")))
        assertNull(GetAppsLink.forSource(Source.Play))
    }

    @Test fun regionGuess() {
        assertEquals("IN", RegionGuess.fromLocale(Locale("en", "IN"), null))
        assertEquals("US", RegionGuess.fromLocale(Locale("en", "US"), "IN"))
        assertEquals("IN", RegionGuess.fromLocale(Locale("en"), "in"))
        assertNull(RegionGuess.fromLocale(Locale("en"), null))
        assertNull(RegionGuess.fromLocale(Locale("es", "419"), "zzz"))
        assertEquals("DE", RegionGuess.fromLocale(Locale("en", "US"), "IN", override = "de"))
        assertEquals("US", RegionGuess.fromLocale(Locale("en", "US"), null, override = "bogus"))
        assertTrue(RegionGuess.isValid("GB"))
    }
}
