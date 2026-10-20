package dev.tvlauncher.nowplaying

import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.home.SpotifyKeyPage
import dev.tvlauncher.link.core.LinkContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MusicScreensaverTest {
    private fun np(state: PlayState = PlayState.PLAYING, updated: Long = 0L, title: String? = "Song", artist: String? = "Artist", pkg: String = "com.spotify.tv.android") =
        NowPlaying(pkg, "Spotify", title, artist, "LP", null, state, 0, 200_000, updated, true, true, true)

    @Test fun showsSongWhilePlaying() {
        val d = AmbientMusicPlan.decide(true, np(), 1_000)
        assertTrue(d.showSong)
        assertNull(d.recheckInMs)
    }

    @Test fun fallsBackWhenNothingPlaysOrOff() {
        assertFalse(AmbientMusicPlan.decide(true, null, 0).showSong)
        assertFalse(AmbientMusicPlan.decide(false, np(), 0).showSong)
        assertFalse(AmbientMusicPlan.decide(true, np(title = null, artist = null), 0).showSong)
    }

    @Test fun pausedExpiresAfterTenMinutes() {
        val paused = np(PlayState.PAUSED, updated = 0)
        val d = AmbientMusicPlan.decide(true, paused, 60_000)
        assertTrue(d.showSong)
        assertEquals(NowPlayingChipModel.PAUSED_VISIBLE_MS - 60_000, d.recheckInMs)
        assertFalse(AmbientMusicPlan.decide(true, paused, NowPlayingChipModel.PAUSED_VISIBLE_MS + 1).showSong)
        assertEquals(10 * 60_000L, NowPlayingChipModel.PAUSED_VISIBLE_MS)
    }

    @Test fun trackChangeAndFade() {
        assertTrue(AmbientMusicPlan.isNewTrack(np(), np(title = "Other")))
        assertFalse(AmbientMusicPlan.isNewTrack(np(), np(state = PlayState.PAUSED)))
        assertEquals(350L, AmbientMusicPlan.fadeMs(lite = false, reducedMotion = false))
        assertEquals(0L, AmbientMusicPlan.fadeMs(lite = true, reducedMotion = false))
    }

    @Test fun ambientModeRow() {
        val d = NowPlayingSettings()
        assertTrue(d.ambientFollows)
        assertEquals(NowPlayingStyle.BLURRED, d.style)
        assertEquals(AmbientMusicPlan.Mode.FULL, AmbientMusicPlan.mode(d))
        for (m in AmbientMusicPlan.Mode.values()) assertEquals(m, AmbientMusicPlan.mode(AmbientMusicPlan.withMode(d, m)))
        assertFalse(AmbientMusicPlan.withMode(d, AmbientMusicPlan.Mode.OFF).showAmbient(true))
        assertFalse(d.followAmbient(accessGranted = false))
    }

    @Test fun settingsJsonKeepsStyleAndOldJsonDefaults() {
        val s = NowPlayingSettings(ambientFollows = false, style = NowPlayingStyle.WASH)
        assertEquals(s, NowPlayingSettings.fromJson(s.toJson()))
        val old = NowPlayingSettings.fromJson("""{"version":1,"enabled":true,"showOnHome":true,"showOnAmbient":true,"backdropFollows":true}""")
        assertTrue(old.ambientFollows)
        assertEquals(NowPlayingStyle.BLURRED, old.style)
        assertEquals(NowPlayingStyle.BLURRED, NowPlayingStyle.of("nonsense"))
        // Only the choices are in the settings JSON (backed up); no key or secret.
        assertFalse(s.toJson().contains("secret", ignoreCase = true))
    }

    @Test fun spotifyOnlyWhenKeyAndBannerStyle() {
        assertTrue(SpotifyArtPolicy.wanted(NowPlayingStyle.BANNER, true, np()))
        assertFalse(SpotifyArtPolicy.wanted(NowPlayingStyle.BANNER, false, np()))
        assertFalse(SpotifyArtPolicy.wanted(NowPlayingStyle.BLURRED, true, np()))
        assertFalse(SpotifyArtPolicy.wanted(NowPlayingStyle.BANNER, true, np(title = null)))
        assertEquals(LinkContract.ART_KIND_ARTIST, SpotifyArtPolicy.kind(NowPlayingStyle.BANNER))
        val k = SpotifyArtPolicy.key(np(), NowPlayingStyle.BANNER)
        assertTrue(LinkContract.isSpotifyKey(k))
        assertEquals(k, LinkContract.spotifyKey("song", "artist", "lp", "artist"))
        assertEquals("content://dev.tvlauncher.link/art/$k", SpotifyArtPolicy.uri(k))
        assertNull(SpotifyArtPolicy.uri("../x"))
        assertEquals("Artwork: Spotify", SpotifyArtPolicy.CREDIT)
    }

    @Test fun byteLruRespectsBudget() {
        val dropped = ArrayList<String>()
        val lru = ByteLru<String>(10, { it.length }, { dropped += it })
        lru.put("a", "aaaa"); lru.put("b", "bbbb")
        lru.get("a")
        lru.put("c", "cccc")
        assertNull(lru.get("b"))
        assertEquals(listOf("bbbb"), dropped)
        assertEquals(8, lru.bytes)
        lru.put("huge", "x".repeat(11))
        assertNull(lru.get("huge"))
        assertEquals(3 * 1024 * 1024, SpotifyArtPolicy.MEMORY_BYTES)
    }

    @Test fun pagesWithinBudget() {
        assertTrue(NowPlayingPageRows.PRIMARY.size <= PageSpec.MAX_PRIMARY)
        assertTrue(SpotifyKeyPage.SPEC.withinBudget)
        assertTrue(SpotifyKeyPage.validCredential("0123456789abcdef0123456789abcdef"))
        assertFalse(SpotifyKeyPage.validCredential("abc def"))
        assertEquals(listOf("Cover", "Blurred cover", "Artist banner", "Colour wash only"), NowPlayingStyle.values().map { it.label })
        assertEquals("Screensaver follows the music", NowPlayingPageRows.title(NowPlayingPageRows.AMBIENT))
    }

    @Test fun secretNeverReachesTheLauncherStorage() {
        val src = File("src/main/java/dev/tvlauncher/home/SpotifyKeyPage.kt").readText()
        assertFalse(src.contains("getSharedPreferences"))
        assertFalse(src.contains("Log."))
        val client = File("src/main/java/dev/tvlauncher/nowplaying/SpotifyArtClient.kt").readText()
        assertFalse(client.contains("getSharedPreferences"))
        assertTrue(client.contains("openFileDescriptor(uri, \"r\")"))
    }
}
