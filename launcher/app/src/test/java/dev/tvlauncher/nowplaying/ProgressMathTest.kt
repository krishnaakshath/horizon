package dev.tvlauncher.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMathTest {
    @Test fun playing_extrapolates() {
        assertEquals(15_000L, ProgressMath.positionNow(PlayState.PLAYING, 10_000, 1_000, 1f, 6_000, 60_000))
    }

    @Test fun playing_respectsSpeed() {
        assertEquals(20_000L, ProgressMath.positionNow(PlayState.PLAYING, 10_000, 0, 2f, 5_000, 60_000))
    }

    @Test fun paused_doesNotMove() {
        assertEquals(10_000L, ProgressMath.positionNow(PlayState.PAUSED, 10_000, 0, 1f, 50_000, 60_000))
        assertEquals(10_000L, ProgressMath.positionNow(PlayState.PLAYING, 10_000, 0, 0f, 50_000, 60_000))
    }

    @Test fun clampsToDuration() {
        assertEquals(60_000L, ProgressMath.positionNow(PlayState.PLAYING, 59_000, 0, 1f, 10_000, 60_000))
        assertEquals(60_000L, ProgressMath.positionNow(PlayState.PAUSED, 70_000, 0, 1f, 0, 60_000))
    }

    @Test fun neverNegative() {
        assertEquals(0L, ProgressMath.positionNow(PlayState.PLAYING, -5_000, 0, 1f, 1_000, 60_000))
        assertEquals(0L, ProgressMath.positionNow(PlayState.PLAYING, 1_000, 0, -4f, 10_000, 60_000))
    }

    @Test fun clockBackwards_noRewind() {
        assertEquals(10_000L, ProgressMath.positionNow(PlayState.PLAYING, 10_000, 5_000, 1f, 1_000, 60_000))
    }

    @Test fun unknownDuration_noUpperClamp() {
        assertEquals(15_000L, ProgressMath.positionNow(PlayState.PLAYING, 10_000, 0, 1f, 5_000, 0))
        assertEquals(15_000L, ProgressMath.positionNow(PlayState.PLAYING, 10_000, 0, 1f, 5_000, -1))
    }

    @Test fun hugeValues_noOverflow() {
        val p = ProgressMath.positionNow(PlayState.PLAYING, Long.MAX_VALUE - 10, 0, 1f, Long.MAX_VALUE, 0)
        assertEquals(true, p >= 0)
    }

    @Test fun fraction() {
        assertEquals(0.5f, ProgressMath.fraction(30_000, 60_000), 0.0001f)
        assertEquals(0f, ProgressMath.fraction(30_000, 0), 0.0001f)
        assertEquals(1f, ProgressMath.fraction(90_000, 60_000), 0.0001f)
        assertEquals(0f, ProgressMath.fraction(-1, 60_000), 0.0001f)
    }

    @Test fun nowPlaying_positionAt() {
        val n = NowPlaying("p.k", "App", "T", "A", null, null, PlayState.PLAYING, 1_000, 10_000, 0, true, false, false, speed = 1f)
        assertEquals(3_000L, n.positionAt(2_000))
    }
}
