package dev.tvlauncher.ui

import dev.tvlauncher.ui.SplashReveal.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashRevealTest {
    @Test fun lite_timeline_totalUnder800ms() {
        val t = SplashReveal.lite()
        assertTrue("total ${t.totalMs}", t.totalMs < 800)
        assertTrue(t.steps.none { it.part == Part.TILE })
        assertTrue(t.steps.any { it.part == Part.CONTENT })
    }

    @Test fun full_timeline_totalUnder1200ms() {
        for (n in 0..20) {
            val t = SplashReveal.full(n)
            assertTrue("n=$n total ${t.totalMs}", t.totalMs < 1200)
        }
    }

    @Test fun full_tileStagger_cappedAndOrdered() {
        val t = SplashReveal.full(20)
        val tiles = t.steps.filter { it.part == Part.TILE }
        assertEquals(SplashReveal.MAX_STAGGERED_TILES, tiles.size)
        assertEquals((0 until tiles.size).toList(), tiles.map { it.index })
        for (i in 1 until tiles.size) assertEquals(40L, tiles[i].startMs - tiles[i - 1].startMs)
    }

    @Test fun full_order_markThenTopBarHeroTilesDock() {
        val t = SplashReveal.full(4)
        val first = t.steps.map { it.part }.distinct()
        assertEquals(listOf(Part.MARK_IN, Part.MARK_OUT, Part.TOP_BAR, Part.HERO, Part.TILE, Part.DOCK), first)
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
}
