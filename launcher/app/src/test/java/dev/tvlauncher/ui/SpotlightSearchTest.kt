package dev.tvlauncher.ui

import dev.tvlauncher.apps.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightSearchTest {
    private fun app(label: String, pkg: String = "com.example." + label.lowercase().filter { it.isLetter() }) =
        AppEntry(pkg, label, "$pkg.Main")
    private fun labels(l: List<AppEntry>) = l.map { it.label }

    @Test fun exact_beats_prefix() {
        val apps = listOf(app("Plexamp"), app("Plex"))
        assertEquals(listOf("Plex", "Plexamp"), labels(SpotlightSearch.rank("plex", apps)))
    }

    @Test fun prefix_beats_substring() {
        val apps = listOf(app("Hotstar"), app("Starz"))
        assertEquals(listOf("Starz", "Hotstar"), labels(SpotlightSearch.rank("star", apps)))
    }

    @Test fun wordPrefix_matches_second_word() {
        val apps = listOf(app("Apple TV"), app("Prime Video"), app("Settings"))
        assertEquals(listOf("Prime Video"), labels(SpotlightSearch.rank("vid", apps)))
        // A word-prefix hit outranks a plain substring hit.
        val ranked = SpotlightSearch.rank("tv", listOf(app("Mytvapp"), app("Apple TV")))
        assertEquals(listOf("Apple TV", "Mytvapp"), labels(ranked))
    }

    @Test fun packageNameMatch_lowestRank() {
        val yt = AppEntry("com.google.android.youtube.tv", "YouTube", "Main")
        val other = AppEntry("com.other.player", "Tube Player", "Main")
        val sub = AppEntry("com.x.y", "MyTubeApp", "Main")
        val ranked = SpotlightSearch.rank("tube", listOf(yt, other, sub))
        // "YouTube" contains "tube" as a substring, so it ranks with substrings; check pkg-only.
        val pkgOnly = AppEntry("com.tubeapps.launcher", "Launcher", "Main")
        val r2 = SpotlightSearch.rank("tube", listOf(pkgOnly, sub, other))
        assertEquals(listOf("Tube Player", "MyTubeApp", "Launcher"), labels(r2))
        assertEquals("Tube Player", ranked.first().label)
    }

    @Test fun emptyQuery_returnsEmpty() {
        val apps = listOf(app("Netflix"))
        assertTrue(SpotlightSearch.rank("", apps).isEmpty())
        assertTrue(SpotlightSearch.rank("   ", apps).isEmpty())
    }

    @Test fun caseInsensitive() {
        val apps = listOf(app("NETFLIX"))
        assertEquals(listOf("NETFLIX"), labels(SpotlightSearch.rank("netFlix", apps)))
    }

    @Test fun diacriticInsensitive() {
        val apps = listOf(app("Pokémon TV"))
        assertEquals(listOf("Pokémon TV"), labels(SpotlightSearch.rank("pokemon", apps)))
        assertEquals(listOf("Pokémon TV"), labels(SpotlightSearch.rank("POKÉ", apps)))
    }

    @Test fun ties_areStable() {
        val apps = listOf(app("Tube B"), app("Tube A"), app("Tube C"))
        assertEquals(listOf("Tube B", "Tube A", "Tube C"), labels(SpotlightSearch.rank("tube", apps)))
    }

    @Test fun noMatch_returnsEmpty() {
        assertTrue(SpotlightSearch.rank("zzz", listOf(app("Netflix"))).isEmpty())
    }

    @Test fun limit12() {
        val apps = (1..20).map { app("App $it", "com.a.app$it") }
        val r = SpotlightSearch.rank("app", apps)
        assertEquals(SpotlightSearch.LIMIT, r.size)
        assertEquals(12, SpotlightSearch.LIMIT)
        assertEquals("App 1", r.first().label)
    }
}
