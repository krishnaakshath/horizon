package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleSizePlannerTest {
    private fun sample(p: SamplePlan): Int = (p as SamplePlan.Sample).n
    private fun decodedPixels(w: Int, h: Int, n: Int) = (w / n).toLong() * (h / n)

    @Test fun normal1920x1080_sample1() {
        assertEquals(SamplePlan.Sample(1), SampleSizePlanner.plan(1920, 1080, 1920, 1080))
        assertEquals(SamplePlan.Sample(1), SampleSizePlanner.plan(800, 600, 1920, 1080))
    }

    @Test fun bomb_100000x1100_isRejectedOrSampledBelowBudget() {
        val p = SampleSizePlanner.plan(100_000, 1_100, 1920, 1080)
        assertTrue(p is SamplePlan.Reject)
        // Even with the source cap lifted, the decoded size stays within 4x the target pixels.
        val lifted = SampleSizePlanner.plan(100_000, 1_100, 1920, 1080, maxSrcPixels = Long.MAX_VALUE)
        // Width 100000 exceeds the 65535 dimension limit, so it is still rejected.
        assertTrue(lifted is SamplePlan.Reject)
        val wide = SampleSizePlanner.plan(60_000, 1_000, 1920, 1080)
        val n = sample(wide)
        assertTrue(decodedPixels(60_000, 1_000, n) <= 4L * 1920 * 1080)
    }

    @Test fun `20000x20000_sampled`() {
        // 400 MP is over the 64 MP default source cap, so it is rejected by default...
        assertTrue(SampleSizePlanner.plan(20_000, 20_000, 1920, 1080) is SamplePlan.Reject)
        // ...and, when allowed, sampled into the decoded-pixel budget.
        val n = sample(SampleSizePlanner.plan(20_000, 20_000, 1920, 1080, maxSrcPixels = Long.MAX_VALUE))
        assertEquals(8, n)
        assertTrue(decodedPixels(20_000, 20_000, n) <= 4L * 1920 * 1080)
        assertTrue(decodedPixels(20_000, 20_000, n / 2) > 4L * 1920 * 1080)
    }

    @Test fun portrait4000x6000_sampled() {
        assertEquals(SamplePlan.Sample(2), SampleSizePlanner.plan(4000, 6000, 1920, 1080))
        // Thumbnails use the same planner: 24 MP -> 320x180 budget 230400 px.
        val t = sample(SampleSizePlanner.plan(4000, 6000, 320, 180))
        assertTrue(decodedPixels(4000, 6000, t) <= 4L * 320 * 180)
        assertEquals(16, t)
    }

    @Test fun zeroDimension_rejected() {
        assertTrue(SampleSizePlanner.plan(0, 1080, 1920, 1080) is SamplePlan.Reject)
        assertTrue(SampleSizePlanner.plan(1920, -1, 1920, 1080) is SamplePlan.Reject)
        assertTrue(SampleSizePlanner.plan(65_536, 10, 1920, 1080) is SamplePlan.Reject)
    }

    @Test fun overflowLongMath_noWrap() {
        // 65535*65535 overflows Int (to a small positive number); Long math must reject it.
        assertTrue(SampleSizePlanner.plan(65_535, 65_535, 1920, 1080) is SamplePlan.Reject)
        val n = sample(SampleSizePlanner.plan(65_535, 65_535, 1920, 1080, maxSrcPixels = Long.MAX_VALUE))
        assertTrue(decodedPixels(65_535, 65_535, n) in 1..4L * 1920 * 1080)
    }

    @Test fun exactBudgetBoundary() {
        // 3840x2160 is exactly 4 x 1920x1080: fits at sample 1.
        assertEquals(SamplePlan.Sample(1), SampleSizePlanner.plan(3840, 2160, 1920, 1080))
        assertEquals(SamplePlan.Sample(2), SampleSizePlanner.plan(3841, 2160, 1920, 1080))
        // Source cap boundary: exactly 64 MP is allowed, one more pixel row is not.
        assertTrue(SampleSizePlanner.plan(8000, 8000, 1920, 1080) is SamplePlan.Sample)
        assertTrue(SampleSizePlanner.plan(8000, 8001, 1920, 1080) is SamplePlan.Reject)
    }

    @Test fun invalidTarget_neverLoopsForever() {
        val p = SampleSizePlanner.plan(4000, 3000, 0, 0)
        assertTrue(p is SamplePlan.Sample)
    }
}
