package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WallpaperSettingsTest {
    @Test fun defaults_areAuroraNoDimBottomLeft() {
        val s = WallpaperSettings()
        assertEquals(WallpaperSource.BuiltIn("aurora"), s.source)
        assertEquals(0, s.dimPercent)
        assertEquals(ClockPosition.BOTTOM_LEFT, s.clockPosition)
        assertEquals(false, s.notificationsEnabled)
        assertEquals(true, s.privacyAppNameOnly)
    }

    @Test fun roundTrip_builtIn() {
        val s = WallpaperSettings(source = WallpaperSource.BuiltIn("nebula"), dimPercent = 30,
            clockPosition = ClockPosition.CENTER, nightDim = true, idleMinutes = 15)
        assertEquals(s, WallpaperSettings.fromJson(s.toJson()))
    }

    @Test fun roundTrip_userImage() {
        val s = WallpaperSettings(source = WallpaperSource.UserImage(File("/data/x/wallpapers/a.jpg")))
        assertEquals(s, WallpaperSettings.fromJson(s.toJson()))
    }

    @Test fun roundTrip_slideshow() {
        val s = WallpaperSettings(source = WallpaperSource.Slideshow(File("/data/x/wallpapers"), 120), shuffle = true)
        assertEquals(s, WallpaperSettings.fromJson(s.toJson()))
    }

    @Test fun malformedOrBlank_returnsDefaults() {
        assertEquals(WallpaperSettings(), WallpaperSettings.fromJson(""))
        assertEquals(WallpaperSettings(), WallpaperSettings.fromJson("{not json"))
        assertEquals(WallpaperSettings(), WallpaperSettings.fromJson("[1,2]"))
    }

    @Test fun dim_isClampedTo0to60() {
        assertEquals(60, WallpaperSettings.fromJson("""{"dim":95}""").dimPercent)
        assertEquals(0, WallpaperSettings.fromJson("""{"dim":-4}""").dimPercent)
        assertEquals(60, WallpaperSettings(dimPercent = 80).normalized().dimPercent)
    }

    @Test fun slideshowInterval_isClamped30to600() {
        val lo = WallpaperSettings.fromJson("""{"source":{"type":"slideshow","folder":"/w","interval":5}}""")
        assertEquals(WallpaperSource.Slideshow(File("/w"), 30), lo.source)
        val hi = WallpaperSettings.fromJson("""{"source":{"type":"slideshow","folder":"/w","interval":9999}}""")
        assertEquals(WallpaperSource.Slideshow(File("/w"), 600), hi.source)
    }

    @Test fun unknownBuiltIn_fallsBackToAurora() {
        val s = WallpaperSettings.fromJson("""{"source":{"type":"builtin","id":"lava"}}""")
        assertEquals(WallpaperSource.BuiltIn("aurora"), s.source)
    }

    @Test fun unknownSourceType_orMissingFile_fallsBackToDefaultSource() {
        assertEquals(WallpaperSource.BuiltIn("aurora"), WallpaperSettings.fromJson("""{"source":{"type":"video"}}""").source)
        assertEquals(WallpaperSource.BuiltIn("aurora"), WallpaperSettings.fromJson("""{"source":{"type":"image"}}""").source)
    }

    @Test fun unknownClockPosition_andWrongTypes_keepOtherFields() {
        val s = WallpaperSettings.fromJson("""{"clock":"MOON","dim":"x","nightDim":true}""")
        assertEquals(ClockPosition.BOTTOM_LEFT, s.clockPosition)
        assertEquals(0, s.dimPercent)
        assertTrue(s.nightDim)
    }

    @Test fun idleMinutes_clamped() {
        assertEquals(1, WallpaperSettings.fromJson("""{"idleMinutes":0}""").idleMinutes)
        assertEquals(120, WallpaperSettings.fromJson("""{"idleMinutes":999}""").idleMinutes)
    }

    @Test fun builtInIds_areTheSixOriginals() {
        assertEquals(listOf("aurora", "dusk", "ocean", "graphite", "nebula", "mono"), WallpaperSource.BUILT_IN_IDS)
    }
}
