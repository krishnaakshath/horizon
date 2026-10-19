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
        // 400 MP is over the 120 MP default source cap, so it is rejected by default...
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
        // Source cap boundary: exactly 120 MP is allowed, one more pixel row is not.
        assertTrue(SampleSizePlanner.plan(12_000, 10_000, 1920, 1080) is SamplePlan.Sample)
        assertTrue(SampleSizePlanner.plan(12_000, 10_001, 1920, 1080) is SamplePlan.Reject)
    }

    @Test fun `12000x9000_acceptedAndSampled`() {
        val n = sample(SampleSizePlanner.plan(12_000, 9_000, 1920, 1080))
        assertEquals(4, n)
        assertTrue(decodedPixels(12_000, 9_000, n) <= 4L * 1920 * 1080)
    }

    @Test fun thumb_4000x6000_coversTargetOnBothAxes() {
        val n = sample(SampleSizePlanner.planCover(4000, 6000, 320, 180))
        assertEquals(8, n)
        assertTrue(4000 / n >= 320 && 6000 / n >= 180)
        assertTrue(decodedPixels(4000, 6000, n) <= SampleSizePlanner.coverBudget(320, 180))
    }

    @Test fun thumb_landscapePhoto_largestSampleStillCovering() {
        // 4000x3000 -> 500x375 at n=8 covers 320x180; n=16 (250 wide) would not.
        assertEquals(SamplePlan.Sample(8), SampleSizePlanner.planCover(4000, 3000, 320, 180))
        assertEquals(SamplePlan.Sample(1), SampleSizePlanner.planCover(300, 200, 320, 180))
    }

    @Test fun thumb_extremeAspect_prefersBudget() {
        val n = sample(SampleSizePlanner.planCover(60_000, 1_000, 320, 180))
        assertTrue(decodedPixels(60_000, 1_000, n) <= SampleSizePlanner.coverBudget(320, 180))
        assertTrue(1_000 / n < 180) // the budget won over covering the height
    }

    @Test fun thumb_rejectsLikePlan() {
        assertTrue(SampleSizePlanner.planCover(0, 10, 320, 180) is SamplePlan.Reject)
        assertTrue(SampleSizePlanner.planCover(100_000, 1_100, 320, 180) is SamplePlan.Reject)
        assertTrue(SampleSizePlanner.planCover(20_000, 20_000, 320, 180) is SamplePlan.Reject)
    }

    @Test fun invalidTarget_neverLoopsForever() {
        val p = SampleSizePlanner.plan(4000, 3000, 0, 0)
        assertTrue(p is SamplePlan.Sample)
    }
}
