package dev.tvlauncher.insights

import dev.tvlauncher.insights.UsageEvent.Type.PAUSED
import dev.tvlauncher.insights.UsageEvent.Type.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeTest {
    @Test fun knownVideo() {
        for (p in listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "in.startv.hotstar", "com.graymatrix.did", "com.jio.media.stb.ondemand",
            "com.apple.atve.androidtv.appletv", "com.plexapp.android"))
            assertEquals(p, AppMode.VIDEO, ModeClassifier.classify(p, null))
    }

    @Test fun knownMusic() {
        assertEquals(AppMode.MUSIC, ModeClassifier.classify("com.google.android.youtube.tvmusic", null))
        assertEquals(AppMode.MUSIC, ModeClassifier.classify("com.spotify.tv.android", null))
    }

    @Test fun games() {
        assertEquals(AppMode.GAME, ModeClassifier.classify("com.google.android.play.games", null))
        assertEquals(AppMode.GAME, ModeClassifier.classify("x.y", 0))
    }

    @Test fun categories() {
        assertEquals(AppMode.VIDEO, ModeClassifier.classify("x.y", 2))
        assertEquals(AppMode.MUSIC, ModeClassifier.classify("x.y", 1))
        assertEquals(AppMode.OTHER, ModeClassifier.classify("x.y", 5))
        assertEquals(AppMode.OTHER, ModeClassifier.classify("x.y", null))
        assertEquals(AppMode.OTHER, ModeClassifier.classify("x.y", -1))
    }

    @Test fun knownListBeatsCategory() {
        assertEquals(AppMode.MUSIC, ModeClassifier.classify("com.spotify.tv.android", 2))
    }

    private val cls: (String) -> AppMode = { ModeClassifier.classify(it, null) }

    @Test fun currentModeFollowsLatestOpenResume() {
        val ev = listOf(UsageEvent("com.spotify.tv.android", RESUMED, 1), UsageEvent("com.netflix.ninja", RESUMED, 5))
        assertEquals(AppMode.VIDEO, ModeDetector.currentMode(ev, cls))
    }

    @Test fun pausedMeansOther() {
        val ev = listOf(UsageEvent("com.netflix.ninja", RESUMED, 1), UsageEvent("com.netflix.ninja", PAUSED, 5))
        assertEquals(AppMode.OTHER, ModeDetector.currentMode(ev, cls))
    }

    @Test fun unsortedAndEmpty() {
        assertEquals(AppMode.OTHER, ModeDetector.currentMode(emptyList(), cls))
        val ev = listOf(UsageEvent("com.netflix.ninja", RESUMED, 9), UsageEvent("com.spotify.tv.android", RESUMED, 2),
            UsageEvent("com.spotify.tv.android", PAUSED, 3))
        assertEquals(AppMode.VIDEO, ModeDetector.currentMode(ev, cls))
    }

    @Test fun stalePausedOfOtherPackageDoesNotClear() {
        val ev = listOf(UsageEvent("com.netflix.ninja", RESUMED, 5), UsageEvent("com.spotify.tv.android", PAUSED, 6))
        assertEquals(AppMode.VIDEO, ModeDetector.currentMode(ev, cls))
    }
}
