package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPlanTest {
    private val files = listOf("a.jpg", "b.png", "c.webp", "d.jpg")

    @Test fun next_sequential_wraps() {
        assertEquals(1, WallpaperPlan.next(files, 0, shuffle = false, seed = 1))
        assertEquals(3, WallpaperPlan.next(files, 2, shuffle = false, seed = 1))
        assertEquals(0, WallpaperPlan.next(files, 3, shuffle = false, seed = 1))
    }

    @Test fun next_emptyList_isMinusOne() {
        assertEquals(-1, WallpaperPlan.next(emptyList<String>(), 0, shuffle = false, seed = 1))
        assertEquals(-1, WallpaperPlan.next(emptyList<String>(), 0, shuffle = true, seed = 1))
    }

    @Test fun next_singleFile_staysZero() {
        assertEquals(0, WallpaperPlan.next(listOf("x"), 0, shuffle = true, seed = 7))
        assertEquals(0, WallpaperPlan.next(listOf("x"), 5, shuffle = false, seed = 7))
    }

    @Test fun next_outOfRangeOrNegativeCurrent_startsAtZero() {
        assertEquals(0, WallpaperPlan.next(files, -1, shuffle = false, seed = 1))
        assertEquals(0, WallpaperPlan.next(files, 99, shuffle = false, seed = 1))
    }

    @Test fun next_shuffle_neverRepeatsCurrent_andIsDeterministic() {
        for (seed in 0L until 200L) {
            for (cur in files.indices) {
                val n = WallpaperPlan.next(files, cur, shuffle = true, seed = seed)
                assertTrue(n in files.indices)
                assertNotEquals(cur, n)
                assertEquals(n, WallpaperPlan.next(files, cur, shuffle = true, seed = seed))
            }
        }
    }

    @Test fun next_shuffle_visitsEveryOtherFile_acrossSeeds() {
        val seen = (0L until 100L).map { WallpaperPlan.next(files, 0, true, it) }.toSet()
        assertEquals(setOf(1, 2, 3), seen)
    }

    @Test fun acceptImages_filtersExtensionSizeAndCap() {
        val cands = listOf(
            WallpaperPlan.Candidate("b.PNG", 1000),
            WallpaperPlan.Candidate("a.jpg", 1000),
            WallpaperPlan.Candidate("big.jpg", 8L * 1024 * 1024 + 1),
            WallpaperPlan.Candidate("ok8.jpeg", 8L * 1024 * 1024),
            WallpaperPlan.Candidate("doc.pdf", 10),
            WallpaperPlan.Candidate(".hidden.jpg", 10),
            WallpaperPlan.Candidate("z.webp", 0),
            WallpaperPlan.Candidate("y.webp", 10)
        )
        assertEquals(listOf("a.jpg", "b.PNG", "ok8.jpeg", "y.webp"), WallpaperPlan.acceptImages(cands).map { it.name })
    }

    @Test fun acceptImages_capsAt24_sortedByName() {
        val many = (0 until 40).map { WallpaperPlan.Candidate("p%02d.jpg".format(39 - it), 10) }
        val out = WallpaperPlan.acceptImages(many)
        assertEquals(24, out.size)
        assertEquals("p00.jpg", out.first().name)
        assertEquals("p23.jpg", out.last().name)
    }

    @Test fun targetSize_capsByMode_andKeepsAspect() {
        assertEquals(WallpaperPlan.Size(1920, 1080), WallpaperPlan.targetSize(3840, 2160, lite = false))
        assertEquals(WallpaperPlan.Size(1280, 720), WallpaperPlan.targetSize(3840, 2160, lite = true))
        assertEquals(WallpaperPlan.Size(1280, 720), WallpaperPlan.targetSize(1280, 720, lite = false))
        assertEquals(WallpaperPlan.Size(1280, 720), WallpaperPlan.targetSize(1920, 1080, lite = true))
        // Portrait-reported metrics are treated as landscape.
        assertEquals(WallpaperPlan.Size(1920, 1080), WallpaperPlan.targetSize(1080, 1920, lite = false))
        assertEquals(WallpaperPlan.Size(1920, 1080), WallpaperPlan.targetSize(0, 0, lite = false))
    }

    @Test fun crossfadeAndDrift_perMode() {
        assertEquals(800L, WallpaperPlan.crossfadeMs(lite = false))
        assertEquals(400L, WallpaperPlan.crossfadeMs(lite = true))
        assertTrue(WallpaperPlan.DRIFT_CYCLE_MS >= 60_000L)
    }

    @Test fun clampInterval() {
        assertEquals(30, WallpaperPlan.clampInterval(1))
        assertEquals(600, WallpaperPlan.clampInterval(10_000))
        assertEquals(90, WallpaperPlan.clampInterval(90))
    }
}
