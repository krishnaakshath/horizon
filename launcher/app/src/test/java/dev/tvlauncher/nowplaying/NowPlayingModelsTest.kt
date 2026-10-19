package dev.tvlauncher.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingModelsTest {
    private fun np(
        title: String? = "Song", artist: String? = "Band", state: PlayState = PlayState.PLAYING,
        updated: Long = 0L, pkg: String = "com.spotify.tv.android"
    ) = NowPlaying(pkg, "Spotify", title, artist, "Album", ArtKey(pkg, title, "Album", null), state,
        0, 200_000, updated, true, true, true)

    // TransportAction ----------------------------------------------------------------------------
    @Test fun transport_constantsMatchPlatform() {
        assertEquals(2L, TransportAction.PLATFORM_PAUSE)
        assertEquals(4L, TransportAction.PLATFORM_PLAY)
        assertEquals(16L, TransportAction.PLATFORM_SKIP_TO_PREVIOUS)
        assertEquals(32L, TransportAction.PLATFORM_SKIP_TO_NEXT)
        assertEquals(512L, TransportAction.PLATFORM_PLAY_PAUSE)
    }

    @Test fun transport_canDo() {
        assertTrue(TransportAction.canDo(512L, TransportAction.PLAY_PAUSE))
        assertTrue(TransportAction.canDo(4L, TransportAction.PLAY_PAUSE))
        assertTrue(TransportAction.canDo(2L, TransportAction.PLAY_PAUSE))
        assertFalse(TransportAction.canDo(0L, TransportAction.PLAY_PAUSE))
        assertTrue(TransportAction.canDo(32L, TransportAction.NEXT))
        assertFalse(TransportAction.canDo(16L, TransportAction.NEXT))
        assertTrue(TransportAction.canDo(16L or 32L, TransportAction.PREV))
        assertFalse(TransportAction.canDo(512L or 32L, TransportAction.PREV))
    }

    // Text ----------------------------------------------------------------------------------------
    @Test fun text_titleAndArtist() {
        assertEquals("Song — Band", NowPlayingText.format(np()))
        assertEquals("Song", NowPlayingText.format(np(artist = " ")))
        assertEquals("Band", NowPlayingText.format(np(title = null)))
        assertEquals("", NowPlayingText.format(null))
    }

    @Test fun text_caps() {
        val longTitle = "T".repeat(80)
        val longArtist = "A".repeat(80)
        val out = NowPlayingText.format(np(title = longTitle, artist = longArtist))
        val (t, a) = out.split(" — ")
        assertEquals(60, t.length)
        assertTrue(t.endsWith("…"))
        assertEquals(40, a.length)
        assertTrue(a.endsWith("…"))
        assertEquals("abc", NowPlayingText.ellipsize("abc", 3))
        assertEquals("ab…", NowPlayingText.ellipsize("abcd", 3))
        assertEquals("", NowPlayingText.ellipsize("abcd", 0))
    }

    @Test fun text_collapsesWhitespace() {
        assertEquals("A B — C", NowPlayingText.format(np(title = " A\n  B ", artist = "C\t")))
    }

    // Chip ----------------------------------------------------------------------------------------
    @Test fun chip_visibleWhilePlaying() {
        val c = NowPlayingChipModel.from(np(updated = 0), now = 3_600_000)
        assertTrue(c.visible)
        assertEquals("♪ Song — Band", c.text)
    }

    @Test fun chip_pausedUnderTenMinutes() {
        assertTrue(NowPlayingChipModel.from(np(state = PlayState.PAUSED, updated = 0), now = 599_999).visible)
        assertFalse(NowPlayingChipModel.from(np(state = PlayState.PAUSED, updated = 0), now = 600_000).visible)
    }

    @Test fun chip_hiddenWhenNullOrStopped() {
        assertFalse(NowPlayingChipModel.from(null, 0).visible)
        assertFalse(NowPlayingChipModel.from(np(state = PlayState.STOPPED), 0).visible)
        assertFalse(NowPlayingChipModel.from(np(title = null, artist = null), 0).visible)
    }

    // Ambient ---------------------------------------------------------------------------------------
    @Test fun ambient_spec() {
        val a = NowPlayingAmbientModel.from(np(), now = 0, showOnAmbient = true)
        assertTrue(a.visible)
        assertEquals("Song", a.line1)
        assertEquals("Band · Spotify", a.line2)
        assertFalse(a.showProgress)
        assertFalse(a.showControls)
        assertEquals(0xFF1DB954.toInt(), a.accent)
        assertFalse(NowPlayingAmbientModel.from(np(), 0, showOnAmbient = false).visible)
        assertFalse(NowPlayingAmbientModel.from(np(state = PlayState.PAUSED), now = 11 * 60_000L, showOnAmbient = true).visible)
    }

    @Test fun accent_fallsBackToNeutral() {
        assertEquals(dev.tvlauncher.ui.BrandColors.NEUTRAL, NowPlayingAccent.forPackage("com.unknown.app"))
        assertEquals(0xFF1DB954.toInt(), NowPlayingAccent.forPackage("com.spotify.tv.android"))
    }

    // Card tick -------------------------------------------------------------------------------------
    @Test fun card_tickOnlyWhileVisibleAndPlaying() {
        assertTrue(NowPlayingCardModel.shouldTick(visible = true, n = np()))
        assertFalse(NowPlayingCardModel.shouldTick(visible = false, n = np()))
        assertFalse(NowPlayingCardModel.shouldTick(visible = true, n = np(state = PlayState.PAUSED)))
        assertFalse(NowPlayingCardModel.shouldTick(visible = true, n = null))
        assertEquals(1_000L, NowPlayingCardModel.TICK_MS)
    }

    @Test fun card_playPauseIcon() {
        assertEquals(NowPlayingCardModel.Icon.PAUSE, NowPlayingCardModel.playPauseIcon(np()))
        assertEquals(NowPlayingCardModel.Icon.PLAY, NowPlayingCardModel.playPauseIcon(np(state = PlayState.PAUSED)))
    }

    // Privacy ---------------------------------------------------------------------------------------
    @Test fun privacy_toStringRedactsContent() {
        val s = np(title = "Secret Song", artist = "Secret Artist").toString()
        assertFalse(s.contains("Secret"))
        assertTrue(s.contains("com.spotify.tv.android"))
        val info = SessionInfo("a.b", "App", "Hidden Title", "Hidden Artist", "Hidden Album", PlayState.PLAYING,
            0, 1f, 0, 0, 0, "content://x")
        assertFalse(info.toString().contains("Hidden"))
        assertFalse(info.toString().contains("content://"))
        assertFalse(ArtKey("a.b", "Hidden", "Hidden", "content://x").toString().contains("Hidden"))
    }

    @Test fun privacy_rules() {
        assertTrue(NowPlayingPrivacy.shouldClear(listenerConnected = false, featureEnabled = true))
        assertTrue(NowPlayingPrivacy.shouldClear(listenerConnected = true, featureEnabled = false))
        assertFalse(NowPlayingPrivacy.shouldClear(listenerConnected = true, featureEnabled = true))
        assertFalse(NowPlayingPrivacy.PERSIST)
        assertFalse(NowPlayingPrivacy.LOG_CONTENT)
        assertEquals(setOf("title", "artist", "album", "art"), NowPlayingPrivacy.KEPT_FIELDS)
    }

    @Test fun privacy_sanitizeTrimsAndCapsText() {
        val info = SessionInfo("a.b", "App", " x ".repeat(200), "y", "", PlayState.PLAYING, 0, 1f, 0, 0, 0, "ftp://evil")
        val clean = NowPlayingPrivacy.sanitize(info)
        assertTrue(clean.title!!.length <= NowPlayingPrivacy.MAX_TEXT)
        assertNull(clean.album)
        assertNull(clean.artUri) // only content:// / android.resource:// / file:// art URIs are read
        assertEquals("content://media/1", NowPlayingPrivacy.sanitize(info.copy(artUri = "content://media/1")).artUri)
        assertEquals("android.resource://a.b/1", NowPlayingPrivacy.sanitize(info.copy(artUri = "android.resource://a.b/1")).artUri)
        assertNull(NowPlayingPrivacy.sanitize(info.copy(artUri = "file:///sdcard/a.jpg")).artUri)
    }

    // Rate limit ----------------------------------------------------------------------------------
    @Test fun rateLimit() {
        val r = EmitRateLimiter(500)
        assertEquals(0L, r.delayFor(now = 1_000))
        r.emitted(1_000)
        assertEquals(400L, r.delayFor(now = 1_100))
        assertEquals(0L, r.delayFor(now = 1_500))
        assertEquals(500L, r.delayFor(now = 900)) // clock went backwards: wait a full interval
    }

    // Art planner ---------------------------------------------------------------------------------
    @Test fun artPlanner() {
        assertEquals(ArtPlan.Sample(1), ArtPlanner.plan(512, 512))
        assertEquals(ArtPlan.Sample(2), ArtPlanner.plan(1024, 1024))
        assertEquals(ArtPlan.Sample(4), ArtPlanner.plan(2000, 2000))
        assertEquals(ArtPlan.Sample(1), ArtPlanner.plan(300, 200))
        assertEquals(ArtPlan.Reject, ArtPlanner.plan(0, 100))
        assertEquals(ArtPlan.Reject, ArtPlanner.plan(10_000, 7_000)) // 70 MP > 64 MP
        assertEquals(ArtPlan.Reject, ArtPlanner.plan(70_000, 10))
        assertTrue(ArtPlanner.plan(8_000, 8_000) is ArtPlan.Sample) // exactly 64 MP is allowed
        val p = ArtPlanner.plan(8_000, 8_000) as ArtPlan.Sample
        assertTrue((8_000 / p.n).toLong() * (8_000 / p.n) <= 512L * 512)
    }

    // Art decode steps: the bounds pass returns no bitmap (inJustDecodeBounds), which must not abort.
    @Test fun artDecode_boundsPassWithoutBitmap_stillDecodes() {
        var opened = 0
        var sampleUsed = -1
        val out = ArtDecode.run(
            open = { opened++; java.io.ByteArrayInputStream(ByteArray(1)) },
            readBounds = { _ -> 2000 to 1000 },
            decode = { _, n -> sampleUsed = n; "bitmap" }
        )
        assertEquals("bitmap", out)
        assertEquals(2, opened)
        assertEquals(4, sampleUsed) // 2000x1000: /2 = 500 000 px > 512^2, /4 fits
    }

    @Test fun artDecode_rejectsBadBoundsAndMissingStreams() {
        assertNull(ArtDecode.run({ java.io.ByteArrayInputStream(ByteArray(1)) }, { _ -> -1 to -1 }, { _, _ -> "x" }))
        assertNull(ArtDecode.run({ java.io.ByteArrayInputStream(ByteArray(1)) }, { _ -> 10_000 to 7_000 }, { _, _ -> "x" }))
        assertNull(ArtDecode.run({ null }, { _ -> 100 to 100 }, { _, _ -> "x" }))
        var calls = 0
        assertNull(ArtDecode.run({ if (calls++ == 0) java.io.ByteArrayInputStream(ByteArray(1)) else null }, { _ -> 100 to 100 }, { _, _ -> "x" }))
    }

    @Test fun artFailureMemo() {
        val k1 = ArtKey("a.b", "t", null, "content://x")
        val memo = ArtFailureMemo()
        assertTrue(memo.shouldTry(k1))
        memo.failed(k1)
        assertFalse(memo.shouldTry(k1))
        assertTrue(memo.shouldTry(k1.copy(title = "other")))
        memo.reset()
        assertTrue(memo.shouldTry(k1))
    }

    // Position update time unknown (lastPositionUpdateTime == 0) ---------------------------------------
    @Test fun positionClock_firstSeenPerTokenAndState() {
        val c = PositionClock()
        assertEquals(1_000L, c.updatedAt("tok", PlayState.PAUSED, platformTime = 0, now = 1_000))
        assertEquals(1_000L, c.updatedAt("tok", PlayState.PAUSED, platformTime = 0, now = 9_000))
        assertEquals(9_500L, c.updatedAt("tok", PlayState.PLAYING, platformTime = 0, now = 9_500))
        assertEquals(42L, c.updatedAt("tok", PlayState.PLAYING, platformTime = 42, now = 9_900))
        assertTrue(PositionClock.isKnown(42))
        assertFalse(PositionClock.isKnown(0))
        c.forget("tok")
        assertEquals(10_000L, c.updatedAt("tok", PlayState.PLAYING, platformTime = 0, now = 10_000))
    }

    @Test fun artPlanner_scaledSize() {
        assertEquals(512 to 256, ArtPlanner.scaledSize(1024, 512))
        assertEquals(300 to 200, ArtPlanner.scaledSize(300, 200))
        assertEquals(256 to 512, ArtPlanner.scaledSize(600, 1200))
        assertEquals(1 to 512, ArtPlanner.scaledSize(1, 10_000))
    }
}
