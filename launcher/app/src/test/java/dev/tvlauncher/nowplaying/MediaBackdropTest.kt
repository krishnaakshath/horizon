package dev.tvlauncher.nowplaying

import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palettes
import dev.tvlauncher.room.RoomMode
import dev.tvlauncher.ui.BoxBlur
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MediaBackdropTest {
    private val MIN = 10 * 60_000L

    private fun np(state: PlayState = PlayState.PLAYING, updated: Long = 0L, pkg: String = "com.spotify.tv.android") = NowPlaying(
        pkg = pkg, appLabel = "Spotify", title = "Song", artist = "Band", album = null, art = ArtKey(pkg, "Song", null, null),
        state = state, positionMs = 0, durationMs = 200_000, updatedAtMs = updated,
        canPlayPause = true, canNext = true, canPrev = true
    )

    private fun input(
        enabled: Boolean = true, item: NowPlaying? = np(), hasArt: Boolean = true, now: Long = 1_000L,
        inShelf: Boolean = true, lastMove: Long = Long.MIN_VALUE
    ) = MediaBackdropPolicy.Input(enabled, item, hasArt, now, inShelf, lastMove)

    private fun decide(i: MediaBackdropPolicy.Input) = MediaBackdropPolicy.decide(i)

    // --- backdrop decision -----------------------------------------------------------------------

    @Test fun playingWithArt_showsArt_evenWithFocusOnATile() {
        val d = decide(input())
        assertEquals(MediaBackdropPolicy.Layer.MEDIA, d.layer)
        assertNull(d.recheckInMs) // playing: nothing expires
    }

    @Test fun fallsBackToBrand_whenOffNoArtNothingOrStopped() {
        assertEquals(MediaBackdropPolicy.Layer.BRAND, decide(input(enabled = false)).layer)
        assertEquals(MediaBackdropPolicy.Layer.BRAND, decide(input(hasArt = false)).layer)
        assertEquals(MediaBackdropPolicy.Layer.BRAND, decide(input(item = null)).layer)
        assertEquals(MediaBackdropPolicy.Layer.BRAND, decide(input(item = np(PlayState.STOPPED))).layer)
        assertEquals(MediaBackdropPolicy.Layer.BRAND, decide(input(item = np(PlayState.ERROR))).layer)
    }

    @Test fun pausedUnderTenMinutes_showsArt_andRechecksAtExpiry() {
        val d = decide(input(item = np(PlayState.PAUSED, updated = 0), now = 9 * 60_000L))
        assertEquals(MediaBackdropPolicy.Layer.MEDIA, d.layer)
        assertEquals(60_000L, d.recheckInMs)
        assertEquals(MediaBackdropPolicy.Layer.BRAND, decide(input(item = np(PlayState.PAUSED, updated = 0), now = MIN)).layer)
    }

    @Test fun movingThroughTheShelf_bringsBrandBack_forSixSeconds() {
        val now = 100_000L
        val moved = decide(input(now = now, lastMove = now - 2_000))
        assertEquals(MediaBackdropPolicy.Layer.BRAND, moved.layer)
        assertEquals(4_000L, moved.recheckInMs)
        assertEquals(MediaBackdropPolicy.Layer.MEDIA, decide(input(now = now, lastMove = now - 6_000)).layer)
        assertEquals(MediaBackdropPolicy.Layer.MEDIA, decide(input(now = now, lastMove = now - 60_000)).layer)
    }

    @Test fun focusOutsideTheShelf_showsArtRightAway() {
        val now = 100_000L
        assertEquals(MediaBackdropPolicy.Layer.MEDIA, decide(input(now = now, inShelf = false, lastMove = now - 100)).layer)
    }

    @Test fun shelfHold_andPausedExpiry_recheckAtTheEarlierOne() {
        val now = MIN - 1_000 // paused item expires in 1 s; shelf hold would be 6 s
        val d = decide(input(item = np(PlayState.PAUSED, updated = 0), now = now, lastMove = now))
        assertEquals(MediaBackdropPolicy.Layer.BRAND, d.layer)
        assertEquals(1_000L, d.recheckInMs)
    }

    @Test fun clockGoingBackwards_doesNotHoldTheBrandForever() {
        val d = decide(input(now = 1_000, lastMove = 5_000))
        assertEquals(MediaBackdropPolicy.Layer.MEDIA, d.layer)
    }

    @Test fun shelfMove_onlyRightAfterAKey() {
        assertTrue(MediaBackdropPolicy.isShelfMove(true, lastKeyAt = 1_000, now = 1_200))
        assertFalse(MediaBackdropPolicy.isShelfMove(true, lastKeyAt = 1_000, now = 3_000)) // e.g. HOME refocus, reload
        assertFalse(MediaBackdropPolicy.isShelfMove(false, lastKeyAt = 1_000, now = 1_100))
        assertFalse(MediaBackdropPolicy.isShelfMove(true, lastKeyAt = Long.MIN_VALUE, now = 1_100))
    }

    @Test fun fade_is350Full_250Lite_noneReduced() {
        assertEquals(350L, MediaBackdropPolicy.fadeMs(EffectsMode.FULL))
        assertEquals(250L, MediaBackdropPolicy.fadeMs(EffectsMode.LITE))
        assertEquals(0L, MediaBackdropPolicy.fadeMs(null))
    }

    // --- timers ------------------------------------------------------------------------------------

    @Test fun expiry_onlyForFreshPausedItems() {
        assertNull(NowPlayingExpiry.delayUntilHidden(np(), 5_000))
        assertNull(NowPlayingExpiry.delayUntilHidden(null, 5_000))
        assertEquals(MIN - 5_000, NowPlayingExpiry.delayUntilHidden(np(PlayState.PAUSED, updated = 0), 5_000))
        assertNull(NowPlayingExpiry.delayUntilHidden(np(PlayState.PAUSED, updated = 0), MIN + 1))
        // Clock behind the update time: a full ten minutes, never negative.
        assertEquals(MIN, NowPlayingExpiry.delayUntilHidden(np(PlayState.PAUSED, updated = 10_000), 0))
    }

    // --- session selection for the owner's apps ---------------------------------------------------

    private fun s(pkg: String, state: PlayState = PlayState.PLAYING, updated: Long = 0L, title: String? = "T") = SessionInfo(
        pkg, pkg, title, "A", null, state, 0, 1f, updated, 1000, 0L, null
    )

    @Test fun selection_ownersApps() {
        val own = "dev.tvlauncher"
        val spotify = s("com.spotify.tv.android", updated = 1)
        val youtube = s("com.google.android.youtube.tv", updated = 9)
        val netflixNoMeta = s("com.netflix.ninja", updated = 20, title = null).copy(artist = null)
        // Both play: the music app wins over the video app.
        assertEquals(spotify, NowPlayingSelector.choose(own, listOf(youtube, spotify)))
        // Spotify paused, YouTube playing: YouTube.
        assertEquals(youtube, NowPlayingSelector.choose(own, listOf(spotify.copy(state = PlayState.PAUSED), youtube)))
        // A session without title/artist (some video apps) is never shown.
        assertEquals(youtube, NowPlayingSelector.choose(own, listOf(netflixNoMeta, youtube)))
        assertNull(NowPlayingSelector.choose(own, listOf(netflixNoMeta)))
        // Our own session never.
        assertNull(NowPlayingSelector.choose(own, listOf(s(own))))
    }

    // --- pixels and contrast ----------------------------------------------------------------------

    @Test fun coverCrop_keepsTheScreenAspect_centred() {
        assertArrayEquals(intArrayOf(0, 0, 512, 512), MediaArtMath.coverCrop(512, 512, 100, 100))
        // Square album art on a 16:9 screen: full width, middle band.
        assertArrayEquals(intArrayOf(0, 112, 512, 400), MediaArtMath.coverCrop(512, 512, 1920, 1080))
        // 16:9 video picture on 16:9: everything.
        assertArrayEquals(intArrayOf(0, 0, 512, 288), MediaArtMath.coverCrop(512, 288, 240, 135))
        // Very wide source: middle columns.
        val c = MediaArtMath.coverCrop(1000, 100, 160, 90)
        assertEquals(100, c[3] - c[1])
        assertEquals(500 - (c[2] - c[0]) / 2, c[0])
        assertArrayEquals(intArrayOf(0, 0, 0, 0), MediaArtMath.coverCrop(0, 0, 10, 10))
    }

    @Test fun pictureSize_isAnEighthFull_quarterLite() {
        assertEquals(240 to 135, MediaArtMath.size(1920, 1080, full = true))
        assertEquals(480 to 270, MediaArtMath.size(1920, 1080, full = false))
        assertEquals(1 to 1, MediaArtMath.size(0, 0, full = true))
        // Budget: two pictures (crossfade) at 4K stay well under 3 MB.
        assertEquals(480 to 270, MediaArtMath.size(3840, 2160, full = false))
        assertEquals(480 to 270, MediaArtMath.size(3840, 2160, full = true))
        val (w, h) = MediaArtMath.size(3840, 2160, full = false)
        assertTrue(2L * w * h * 4 < 3L * 1024 * 1024)
    }

    /** The same steps as MediaBackdrop.process, on raw pixels. */
    private fun pipeline(src: IntArray, w: Int, h: Int, full: Boolean, target: Double): IntArray {
        val px = src.copyOf()
        if (full) BoxBlur.blur(px, w, h, MediaArtMath.BLUR_RADIUS)
        val dominant = BrandColors.dominantColor(px)
        if (!full) MediaArtMath.accentWash(px, w, h, BrandColors.backdropAccent(dominant))
        MediaArtMath.scrim(px, if (full) MediaArtMath.SCRIM_FULL else MediaArtMath.SCRIM_LITE)
        MediaArtMath.clampContrast(px, target)
        return px
    }

    private fun arts(w: Int, h: Int): List<IntArray> {
        val r = Random(7)
        return listOf(
            IntArray(w * h) { 0xFFFFFFFF.toInt() },              // white sleeve
            IntArray(w * h) { 0xFFFFE600.toInt() },              // bright yellow
            IntArray(w * h) { 0xFF00FF66.toInt() },              // neon green
            IntArray(w * h) { if ((it / w + it % w) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() },
            IntArray(w * h) { 0xFF000000.toInt() or r.nextInt(0x1000000) }, // noise
            IntArray(w * h) { 0xFF101418.toInt() }               // already dark
        )
    }

    @Test fun textContrast_atLeast4_5_overTheArt_inEveryRoom_fullAndLite() {
        val w = 24; val h = 14
        for (mode in RoomMode.values()) {
            val p = Palettes.of(mode)
            val target = MediaArtMath.targetLuminance(p, Theme.TEXT_2)
            for (full in listOf(true, false)) for (art in arts(w, h)) {
                val out = pipeline(art, w, h, full, target)
                for (c in out) for (text in listOf(p.textPrimary, p.textSecondary, Theme.TEXT_2, Theme.TEXT)) {
                    val ratio = Contrast.ratio(text, c)
                    assertTrue("$mode full=$full text=${Integer.toHexString(text)} bg=${Integer.toHexString(c)} ratio=$ratio",
                        ratio >= MediaArtMath.MIN_CONTRAST)
                }
            }
        }
    }

    @Test fun darkArt_isNotDarkenedFurtherByTheClamp() {
        val px = IntArray(16) { 0xFF101418.toInt() }
        MediaArtMath.clampContrast(px, MediaArtMath.targetLuminance(Palettes.of(RoomMode.STANDARD)))
        assertTrue(px.all { it == 0xFF101418.toInt() })
    }

    @Test fun darkening_keepsTheHue() {
        val px = IntArray(4) { 0xFFFF2020.toInt() }
        MediaArtMath.clampContrast(px, MediaArtMath.targetLuminance(Palettes.of(RoomMode.CINEMA)))
        val c = px[0]
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        assertTrue(r > g && r > b)
        assertTrue(r < 0xFF)
    }

    @Test fun maxBackgroundLuminance_matchesWcag() {
        // White text: 4.5:1 needs a background at or below L 0.1833.
        assertEquals(0.18333, MediaArtMath.maxBackgroundLuminance(0xFFFFFFFF.toInt()), 1e-4)
        assertEquals(1.0, MediaArtMath.darkenFactor(0.1, 0.5), 0.0)
        assertEquals(0.5 * MediaArtMath.SAFETY, MediaArtMath.darkenFactor(1.0, 0.5), 1e-9)
        assertEquals(1.0, MediaArtMath.darkenFactor(0.0, 0.5), 0.0)
        // A darker room's text is dimmer, so its art goes darker.
        assertTrue(MediaArtMath.targetLuminance(Palettes.of(RoomMode.CINEMA)) < MediaArtMath.targetLuminance(Palettes.of(RoomMode.BRIGHT)))
    }

    @Test fun liteAccentWash_tintsTopLeftOnly_andDominantColourComesFromTheArt() {
        val w = 10; val h = 6
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        val red = BrandColors.dominantColor(IntArray(64) { 0xFFE02020.toInt() })
        assertTrue(((red shr 16) and 0xFF) > 0xC0)
        MediaArtMath.accentWash(px, w, h, red)
        assertTrue(((px[0] shr 16) and 0xFF) > 0x40)
        assertEquals(0xFF000000.toInt(), px[w * h - 1])
    }

    @Test fun stops_sampleTopLeftCentreBottomRight() {
        val w = 10; val h = 10
        val px = IntArray(w * h) { i -> if (i % w < 5 && i / w < 5) 1 else if (i % w >= 5 && i / w >= 5) 3 else 2 }
        val s = MediaArtMath.stops(px, w, h)
        assertEquals(1, s[0]); assertEquals(3, s[2])
        assertEquals(3, MediaArtMath.stops(IntArray(0), 0, 0).size)
    }

    // --- settings page ----------------------------------------------------------------------------

    @Test fun page_rowsPassTheGuard_withinBudget_noDeveloperText() {
        val spec = PageSpec(NowPlayingPageRows.ID, NowPlayingPageRows.PRIMARY)
        assertTrue(spec.withinBudget)
        assertTrue(NowPlayingPageRows.PRIMARY.size <= PageSpec.MAX_PRIMARY)
        for (s in listOf(NowPlayingSettings(), NowPlayingSettings(nowPlayingEnabled = false))) {
            val g = RowGuard(spec)
            NowPlayingPageRows.rows(s).forEach { g.claim(it, false) }
        }
        val texts = NowPlayingPageRows.PRIMARY.flatMap { listOfNotNull(NowPlayingPageRows.title(it), NowPlayingPageRows.subtitle(it)) } +
            NowPlayingPageRows.LEAD + NowPlayingPageRows.ACCESS_ON + NowPlayingPageRows.ACCESS_OFF
        for (t in texts) for (bad in listOf("adb", "listener", "MediaSession", "SECURE", "package", "debug", "null")) {
            assertFalse("'$t' contains '$bad'", t.contains(bad, ignoreCase = true))
        }
    }

    @Test fun page_togglesEachSetting_andHidesTheWhereRowsWhenOff() {
        val d = NowPlayingSettings()
        assertTrue(d.backdropFollows && d.showOnHome && d.showOnAmbient && d.nowPlayingEnabled) // all on by default
        assertFalse(NowPlayingPageRows.toggle(d, NowPlayingPageRows.BACKDROP).backdropFollows)
        assertFalse(NowPlayingPageRows.toggle(d, NowPlayingPageRows.MINI_PLAYER).showOnHome)
        assertFalse(NowPlayingPageRows.toggle(d, NowPlayingPageRows.AMBIENT).showOnAmbient)
        assertFalse(NowPlayingPageRows.toggle(d, NowPlayingPageRows.ENABLED).nowPlayingEnabled)
        assertEquals(d, NowPlayingPageRows.toggle(d, NowPlayingPageRows.ACCESS))
        for (k in NowPlayingPageRows.PRIMARY - NowPlayingPageRows.ACCESS) {
            assertEquals(!NowPlayingPageRows.isOn(d, k), NowPlayingPageRows.isOn(NowPlayingPageRows.toggle(d, k), k))
        }
        assertEquals(listOf(NowPlayingPageRows.ENABLED, NowPlayingPageRows.ACCESS), NowPlayingPageRows.rows(d.copy(nowPlayingEnabled = false)))
        assertEquals("On", NowPlayingPageRows.summary(d))
        assertEquals("Off", NowPlayingPageRows.summary(d.copy(nowPlayingEnabled = false)))
    }

    @Test fun settings_backdropFollows_roundTrips_andDefaultsOnForOldJson() {
        val s = NowPlayingSettings(backdropFollows = false)
        assertEquals(s, NowPlayingSettings.fromJson(s.toJson()))
        assertTrue(NowPlayingSettings.fromJson("{\"version\":1,\"enabled\":true,\"showOnHome\":true,\"showOnAmbient\":true}").backdropFollows)
        assertTrue(NowPlayingSettings().followBackdrop(accessGranted = true))
        assertFalse(NowPlayingSettings().followBackdrop(accessGranted = false))
        assertFalse(NowPlayingSettings(nowPlayingEnabled = false).followBackdrop(accessGranted = true))
    }

    // --- probe ------------------------------------------------------------------------------------

    private fun sample(bitmapKey: String? = null, uri: String? = null, title: Boolean = true) = NowPlayingProbe.Sample(
        "com.google.android.youtube.tv", 3, title, true, false, false, bitmapKey, if (bitmapKey != null) 640 else 0,
        if (bitmapKey != null) 360 else 0, uri, 0L, TransportAction.PLATFORM_PLAY_PAUSE, true
    )

    @Test fun probe_reportsArtKind_andNeverContent() {
        assertEquals("bitmap", NowPlayingProbe.artVerdict(sample(bitmapKey = "ART")))
        assertEquals("uri", NowPlayingProbe.artVerdict(sample(uri = "content://media/1")))
        assertEquals("uri-rejected(https)", NowPlayingProbe.artVerdict(sample(uri = "https://i.ytimg.com/vi/secret/hq.jpg")))
        assertEquals("none", NowPlayingProbe.artVerdict(sample()))
        val line = NowPlayingProbe.describe(sample(uri = "https://i.ytimg.com/vi/secret/hq.jpg"), chosen = true)
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("ytimg"))
        assertTrue(line.contains("state=PLAYING"))
        assertTrue(line.contains("actions=playPause"))
        assertTrue(line.contains("shown=yes"))
        assertTrue(NowPlayingProbe.describe(sample(bitmapKey = "ALBUM_ART"), false).contains("bitmap=ALBUM_ART:640x360"))
        assertTrue(NowPlayingProbe.describe(sample(title = false), false).contains("meta=artist"))
    }
}
