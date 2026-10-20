package dev.tvlauncher.link.spotify

import dev.tvlauncher.link.core.LinkContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpotifyCoreTest {
    @Test fun tokenParsing() {
        val t = SpotifyCore.parseToken("""{"access_token":"abc","token_type":"Bearer","expires_in":3600}""", 1000L)!!
        assertEquals("abc", t.bearer)
        assertEquals(1000L + 3_600_000L, t.expiresAtMs)
        assertTrue(t.valid(1000L))
        assertFalse(t.valid(1000L + 3_600_000L - 30_000L)) // inside the renewal margin
        assertFalse(t.toString().contains("abc"))
        assertNull(SpotifyCore.parseToken("""{"error":"invalid_client"}""", 0))
        assertNull(SpotifyCore.parseToken("""{"access_token":"a","expires_in":0}""", 0))
        assertNull(SpotifyCore.parseToken("""{"access_token":"a\r\nX: y","expires_in":10}""", 0))
        assertNull(SpotifyCore.parseToken("not json", 0))
        assertNull(SpotifyCore.parseToken(null, 0))
    }

    @Test fun basicAuthAndCredentials() {
        assertEquals("Basic YWJjOmRlZg==", SpotifyCore.basicAuth("abc", "def"))
        assertTrue(SpotifyCore.validCredential("0123456789abcdef0123456789abcdef"))
        assertFalse(SpotifyCore.validCredential("short"))
        assertFalse(SpotifyCore.validCredential("0123456789abcdef:0123456789abcdef"))
        assertFalse(SpotifyCore.validCredential(null))
    }

    @Test fun queryBuilding() {
        assertEquals("track:\"Hey Jude\" artist:\"The Beatles\"",
            SpotifyCore.query("Hey Jude - Remastered 2015", "The Beatles", "1"))
        assertEquals("track:\"Song\" artist:\"A\"", SpotifyCore.query("Song (feat. B)", "A, B", null))
        assertEquals("track:\"Song\" album:\"LP\"", SpotifyCore.query("Song", null, "LP"))
        assertEquals("track:\"Say  hi\"", SpotifyCore.query("Say \"hi\"", " ", null))
        assertNull(SpotifyCore.query(null, "A", null))
        assertNull(SpotifyCore.query("  ", "A", null))
        val url = SpotifyCore.searchUrl("track:\"a b\" artist:\"c&d\"")
        assertTrue(url.startsWith("https://api.spotify.com/v1/search?type=track&limit=1&q="))
        assertFalse(url.contains(" "))
        assertTrue(url.contains("%26"))
    }

    @Test fun searchAndArtistParsing() {
        val json = """{"tracks":{"items":[{"album":{"images":[
            {"url":"https://i.scdn.co/image/small","width":64,"height":64},
            {"url":"https://i.scdn.co/image/big","width":640,"height":640},
            {"url":"http://evil/plain","width":2000,"height":2000}]},
            "artists":[{"id":"0OdUWJ0sBjDrqHygGUXeCF"}]}]}}"""
        val hit = SpotifyCore.parseSearch(json)!!
        assertEquals("https://i.scdn.co/image/big", hit.albumImage)
        assertEquals("0OdUWJ0sBjDrqHygGUXeCF", hit.artistId)
        assertNull(SpotifyCore.parseSearch("""{"tracks":{"items":[]}}"""))
        assertNull(SpotifyCore.parseSearch("""{"tracks":{"items":[{"artists":[{"id":"../../x"}]}]}}"""))
        assertEquals("https://i.scdn.co/a", SpotifyCore.parseArtistImage(
            """{"images":[{"url":"https://i.scdn.co/a","width":1000,"height":1000},{"url":"https://i.scdn.co/b","width":160,"height":160}]}"""))
        assertNull(SpotifyCore.parseArtistImage("""{"images":[]}"""))
        assertEquals("https://api.spotify.com/v1/artists/0OdUWJ0sBjDrqHygGUXeCF", SpotifyCore.artistUrl("0OdUWJ0sBjDrqHygGUXeCF"))
        assertNull(SpotifyCore.artistUrl("a/../b?c"))
    }

    @Test fun cacheKeys() {
        val a = SpotifyCore.trackKey("Song", "Artist", "LP", SpotifyCore.Kind.ALBUM)
        assertEquals(a, SpotifyCore.trackKey(" song ", "ARTIST", "lp", SpotifyCore.Kind.ALBUM))
        assertNotEquals(a, SpotifyCore.trackKey("Song", "Artist", "LP", SpotifyCore.Kind.ARTIST))
        assertTrue(SpotifyCore.validKey(a))
        assertEquals(a, LinkContract.spotifyKey("Song", "Artist", "LP", LinkContract.ART_KIND_ALBUM))
        assertFalse(SpotifyCore.validKey("../../databases/x"))
        assertFalse(SpotifyCore.validKey(a.uppercase()))
    }

    @Test fun imagePlanning() {
        assertEquals(1, SpotifyCore.sampleSize(640, 640))
        assertEquals(4, SpotifyCore.sampleSize(4000, 3000))
        assertNull(SpotifyCore.sampleSize(50_000, 50_000))
        assertNull(SpotifyCore.sampleSize(0, 10))
        assertEquals(720 to 720, SpotifyCore.fit(1000, 1000))
        assertEquals(1280 to 720, SpotifyCore.fit(2560, 1440))
        assertEquals(300 to 300, SpotifyCore.fit(300, 300))
        val files = (1..25).map { "f$it.jpg" to it.toLong() }
        assertEquals((1..5).map { "f$it.jpg" }, SpotifyCore.evict(files))
        assertTrue(SpotifyCore.evict(files.take(20)).isEmpty())
        assertEquals(5_000L, SpotifyCore.retryAfterMs("5"))
        assertEquals(3_600_000L, SpotifyCore.retryAfterMs("999999"))
        assertNull(SpotifyCore.retryAfterMs("soon"))
    }

    @Test fun rateLimiter() {
        val l = TrackLimiter()
        assertTrue(l.mayRequest("k1", 0))
        assertFalse(l.mayRequest("k1", 10)) // one request per track
        assertTrue(l.mayRequest("k2", 10))
        l.failure(100)
        assertFalse(l.mayRequest("k3", 100 + TrackLimiter.BASE_MS - 1))
        assertTrue(l.mayRequest("k3", 100 + TrackLimiter.BASE_MS))
        l.failure(0); l.failure(0)
        assertEquals(TrackLimiter.BASE_MS * 4, l.blockedUntil()) // doubles
        l.failure(0, retryAfterMs = 7_000)
        assertEquals(7_000L, l.blockedUntil())
        repeat(20) { l.failure(0) }
        assertEquals(TrackLimiter.MAX_MS, l.blockedUntil())
        l.forget("k1"); l.reset()
        assertTrue(l.mayRequest("k1", 0))
    }

    @Test fun providerContract() {
        assertEquals("content://dev.tvlauncher.link/art", LinkContract.URI_ART)
        assertEquals(3, LinkContract.SPOTIFY_COLUMNS.size)
        assertFalse(LinkContract.SPOTIFY_COLUMNS.any { it.contains("secret") })
        val src = File("src/main/java/dev/tvlauncher/link/LinkProvider.kt").readText()
        assertTrue(src.contains("mode != \"r\""))
        assertTrue(src.contains("SpotifyArt.file("))
        // The secret is never logged or put in a cursor or bundle.
        val art = File("src/main/java/dev/tvlauncher/link/spotify/SpotifyArt.kt").readText()
        assertFalse(art.contains("Log."))
        assertFalse(src.contains("clientSecret"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:readPermission=\"dev.tvlauncher.permission.LINK\""))
    }
}
