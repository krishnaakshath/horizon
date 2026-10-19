package dev.tvlauncher.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingSelectorTest {
    private val OWN = "dev.tvlauncher"

    private fun s(
        pkg: String, state: PlayState = PlayState.PLAYING, updated: Long = 0L,
        title: String? = "Song", artist: String? = "Band"
    ) = SessionInfo(
        pkg = pkg, appLabel = pkg, title = title, artist = artist, album = null, state = state,
        positionMs = 0, speed = 1f, updatedAtMs = updated, durationMs = 1000, actions = 0L, artUri = null
    )

    @Test fun empty_isNull() = assertNull(NowPlayingSelector.choose(OWN, emptyList()))

    @Test fun playing_beatsPaused_evenIfOlder() {
        val a = s("com.video", PlayState.PAUSED, updated = 100)
        val b = s("com.other", PlayState.PLAYING, updated = 1)
        assertEquals(b, NowPlayingSelector.choose(OWN, listOf(a, b)))
    }

    @Test fun buffering_countsAsPlaying() {
        val a = s("com.x", PlayState.PAUSED, updated = 100)
        val b = s("com.y", PlayState.BUFFERING, updated = 1)
        assertEquals(b, NowPlayingSelector.choose(OWN, listOf(a, b)))
    }

    @Test fun audioApp_beatsVideoApp_whenBothPlay() {
        val video = s("com.netflix.ninja", updated = 500)
        val audio = s("com.spotify.tv.android", updated = 10)
        assertEquals(audio, NowPlayingSelector.choose(OWN, listOf(video, audio)))
        val ytm = s("com.google.android.youtube.tvmusic", updated = 1)
        assertEquals(ytm, NowPlayingSelector.choose(OWN, listOf(video, ytm)))
    }

    @Test fun mostRecent_winsAmongEquals() {
        val a = s("com.a", updated = 5)
        val b = s("com.b", updated = 9)
        assertEquals(b, NowPlayingSelector.choose(OWN, listOf(a, b)))
        assertEquals(b, NowPlayingSelector.choose(OWN, listOf(b, a)))
    }

    @Test fun blankMetadata_ignored() {
        val blank = s("com.spotify.tv.android", title = "  ", artist = null, updated = 99)
        val nulls = s("com.spotify.tv.android", title = null, artist = null, updated = 99)
        val ok = s("com.video", PlayState.PAUSED)
        assertEquals(ok, NowPlayingSelector.choose(OWN, listOf(blank, nulls, ok)))
    }

    @Test fun ownPackage_ignored() {
        assertNull(NowPlayingSelector.choose(OWN, listOf(s("dev.tvlauncher"))))
        assertNull(NowPlayingSelector.choose("dev.tvlauncher.helper", listOf(s("dev.tvlauncher.helper"))))
    }

    @Test fun stoppedNoneError_ignored() {
        assertNull(NowPlayingSelector.choose(OWN, listOf(s("a.b", PlayState.STOPPED), s("a.c", PlayState.NONE), s("a.d", PlayState.ERROR))))
    }

    @Test fun audioClassification() {
        listOf("com.spotify.tv.android", "com.spotify.music", "com.amazon.music.tv", "com.aspiro.tidal",
            "deezer.android.app", "com.pandora.android.atv", "com.jio.media.jiobeats", "tunein.player")
            .forEach { assertEquals(it, true, NowPlayingApps.isAudio(it)) }
        listOf("com.netflix.ninja", "com.google.android.youtube.tv", "org.videolan.vlc")
            .forEach { assertEquals(it, false, NowPlayingApps.isAudio(it)) }
    }

    @Test fun stateFromPlatform() {
        // android.media.session.PlaybackState constants.
        assertEquals(PlayState.PLAYING, PlayState.fromPlatform(3))
        assertEquals(PlayState.PAUSED, PlayState.fromPlatform(2))
        assertEquals(PlayState.BUFFERING, PlayState.fromPlatform(6))
        assertEquals(PlayState.STOPPED, PlayState.fromPlatform(1))
        assertEquals(PlayState.NONE, PlayState.fromPlatform(0))
        assertEquals(PlayState.ERROR, PlayState.fromPlatform(7))
        assertEquals(PlayState.PLAYING, PlayState.fromPlatform(4)) // fast forwarding
        assertEquals(PlayState.NONE, PlayState.fromPlatform(42))
    }

    @Test fun toNowPlaying_copiesFields_andCapabilities() {
        val info = s("com.spotify.tv.android").copy(
            actions = TransportAction.PLATFORM_PLAY_PAUSE or TransportAction.PLATFORM_SKIP_TO_NEXT
        )
        val n = info.toNowPlaying()
        assertEquals("Song", n.title)
        assertEquals(true, n.canPlayPause)
        assertEquals(true, n.canNext)
        assertEquals(false, n.canPrev)
    }
}
