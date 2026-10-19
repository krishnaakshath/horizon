package dev.tvlauncher.ui

import dev.tvlauncher.ui.SplashReveal.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashRevealTest {
    @Test fun lite_timeline_withinBudget() {
        val t = SplashReveal.lite()
        assertTrue("total ${t.totalMs}", t.totalMs <= SplashReveal.LITE_BUDGET_MS)
        assertEquals(700L, SplashReveal.LITE_BUDGET_MS)
        assertTrue(t.steps.none { it.part == Part.TILE })
        assertTrue(t.steps.any { it.part == Part.CONTENT })
    }

    @Test fun full_timeline_withinBudget() {
        assertEquals(1100L, SplashReveal.FULL_BUDGET_MS)
        for (n in 0..20) {
            val t = SplashReveal.full(n)
            assertTrue("n=$n total ${t.totalMs}", t.totalMs <= SplashReveal.FULL_BUDGET_MS)
        }
    }

    @Test fun full_tileStagger_cappedAndOrdered() {
        val t = SplashReveal.full(20)
        val tiles = t.steps.filter { it.part == Part.TILE }
        assertEquals(SplashReveal.MAX_STAGGERED_TILES, tiles.size)
        assertEquals((0 until tiles.size).toList(), tiles.map { it.index })
        for (i in 1 until tiles.size) assertEquals(SplashReveal.TILE_STAGGER_MS, tiles[i].startMs - tiles[i - 1].startMs)
    }

    @Test fun full_order_sunriseThenHome() {
        val t = SplashReveal.full(4)
        val first = t.steps.map { it.part }.distinct()
        assertEquals(
            listOf(Part.LINE, Part.SUN, Part.MARK, Part.CAPTION, Part.MARK_OUT, Part.TOP_BAR, Part.HERO, Part.TILE, Part.DOCK),
            first
        )
    }

    @Test fun lite_order_sunriseThenContent() {
        assertEquals(
            listOf(Part.LINE, Part.SUN, Part.MARK, Part.CAPTION, Part.MARK_OUT, Part.CONTENT),
            SplashReveal.lite().steps.map { it.part }
        )
    }

    @Test fun intro_isLineSunMarkCaption_andEndsAtMarkOut() {
        assertEquals(setOf(Part.LINE, Part.SUN, Part.MARK, Part.CAPTION), SplashReveal.INTRO)
        for (t in listOf(SplashReveal.lite(), SplashReveal.full(0), SplashReveal.full(6))) {
            val out = t.first(Part.MARK_OUT)!!
            assertEquals(out.startMs, t.introEndMs)
            // The intro finishes before the mark leaves; nothing of the home starts before that.
            t.steps.filter { it.part in SplashReveal.INTRO }.forEach { assertTrue(it.startMs + it.durationMs <= out.startMs) }
            t.steps.filter { it.part !in SplashReveal.INTRO }.forEach { assertTrue(it.startMs >= out.startMs) }
        }
    }

    @Test fun sun_risesOnTheLine_markAfterSun_captionAfterMark() {
        for (t in listOf(SplashReveal.lite(), SplashReveal.full(3))) {
            val line = t.first(Part.LINE)!!; val sun = t.first(Part.SUN)!!
            val mark = t.first(Part.MARK)!!; val cap = t.first(Part.CAPTION)!!
            assertTrue(sun.startMs > line.startMs && sun.startMs < line.startMs + line.durationMs)
            assertTrue(mark.startMs > sun.startMs)
            assertTrue(cap.startMs > mark.startMs)
        }
    }

    @Test fun timeline_isMonotonic_noOverlapGaps() {
        for (t in listOf(SplashReveal.lite(), SplashReveal.full(0), SplashReveal.full(3), SplashReveal.full(12))) {
            var lastStart = 0L
            var reachedEnd = 0L
            for (s in t.steps) {
                assertTrue("non-negative", s.startMs >= 0 && s.durationMs > 0)
                assertTrue("monotonic start", s.startMs >= lastStart)
                assertTrue("no idle gap before ${s.part}", s.startMs <= reachedEnd)
                lastStart = s.startMs
                reachedEnd = maxOf(reachedEnd, s.startMs + s.durationMs)
            }
            assertEquals(reachedEnd, t.totalMs)
        }
    }

    @Test fun totalMs_coversEveryTimeline() {
        assertEquals(SplashReveal.full(SplashReveal.MAX_STAGGERED_TILES).totalMs, SplashReveal.totalMs(full = true))
        assertEquals(SplashReveal.lite().totalMs, SplashReveal.totalMs(full = false))
        for (n in 0..12) assertTrue(SplashReveal.full(n).totalMs <= SplashReveal.totalMs(full = true))
    }

    @Test fun look_colorsFollowTheAccent() {
        assertEquals(SplashLook.HALO_CENTER, SplashLook.halo(SplashLook.DEFAULT_ACCENT))
        val red = 0xFFE50914.toInt()
        assertEquals(0x00E50914, SplashLook.halo(red) and 0x00FFFFFF)
        assertEquals(SplashLook.GLOW_ALPHA, SplashLook.glow(red) ushr 24)
        // The sun's core is opaque and lighter than the accent in every channel.
        val core = SplashLook.sunCore(red)
        assertEquals(0xFF, core ushr 24)
        for (sh in listOf(16, 8, 0)) assertTrue(((core shr sh) and 0xFF) >= ((red shr sh) and 0xFF))
        val dark = SplashLook.sunCore(0xFF000000.toInt())
        assertTrue(((dark shr 16) and 0xFF) > 0x90) // even a black accent gives a visible sun
    }
}

class HorizonLoaderMathTest {
    @Test fun position_goesThereAndBack_continuously() {
        assertEquals(0f, HorizonLoaderMath.position(0f), 1e-6f)
        assertEquals(1f, HorizonLoaderMath.position(0.5f), 1e-6f)
        assertEquals(0f, HorizonLoaderMath.position(1f), 1e-6f)
        var prev = HorizonLoaderMath.position(0f)
        for (i in 1..100) {
            val x = HorizonLoaderMath.position(i / 100f)
            assertTrue(x in 0f..1f)
            assertTrue("jump at $i", kotlin.math.abs(x - prev) < 0.05f)
            prev = x
        }
    }

    @Test fun position_monotonicEachWay() {
        for (i in 1..50) assertTrue(HorizonLoaderMath.position(i / 100f) >= HorizonLoaderMath.position((i - 1) / 100f))
        for (i in 51..100) assertTrue(HorizonLoaderMath.position(i / 100f) <= HorizonLoaderMath.position((i - 1) / 100f))
    }

    @Test fun brightness_peaksMidLine() {
        assertEquals(0f, HorizonLoaderMath.brightness(0f), 1e-6f)
        assertEquals(1f, HorizonLoaderMath.brightness(0.25f), 1e-3f)
        for (i in 0..100) assertTrue(HorizonLoaderMath.brightness(i / 100f) in 0f..1f)
        assertTrue(HorizonLoaderMath.CYCLE_MS in 900L..2000L)
    }
}
