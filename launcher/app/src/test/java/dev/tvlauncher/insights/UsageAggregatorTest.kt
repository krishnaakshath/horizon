package dev.tvlauncher.insights

import dev.tvlauncher.insights.UsageEvent.Type.PAUSED
import dev.tvlauncher.insights.UsageEvent.Type.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {
    private val min = 60_000L
    private val from = 0L
    private val to = 100 * min
    private fun r(p: String, t: Long) = UsageEvent(p, RESUMED, t)
    private fun p(p: String, t: Long) = UsageEvent(p, PAUSED, t)
    private fun agg(vararg e: UsageEvent, ignore: Set<String> = emptySet()) =
        UsageAggregator.aggregate(e.toList(), from, to, ignore)

    @Test fun simpleSession() {
        assertEquals(mapOf("a" to 10 * min), agg(r("a", 5 * min), p("a", 15 * min)))
    }

    @Test fun sessionsAccumulate() {
        assertEquals(mapOf("a" to 15 * min), agg(r("a", 0), p("a", 10 * min), r("a", 20 * min), p("a", 25 * min)))
    }

    @Test fun unmatchedResumeEndsAtWindowEnd() {
        assertEquals(mapOf("a" to 40 * min), agg(r("a", 60 * min)))
    }

    @Test fun pausedWithoutResumeStartsAtWindowStart() {
        assertEquals(mapOf("a" to 7 * min), agg(p("a", 7 * min)))
    }

    @Test fun clipsToWindow() {
        val m = UsageAggregator.aggregate(listOf(r("a", -5 * min), p("a", 3 * min), r("b", 98 * min), p("b", 120 * min)), from, to, emptySet())
        assertEquals(3 * min, m["a"])
        assertEquals(2 * min, m["b"])
    }

    @Test fun ignoredPackagesDropped() {
        assertEquals(mapOf("a" to 5 * min), agg(r("a", 0), p("a", 5 * min), r("sys", 6 * min), p("sys", 9 * min), ignore = setOf("sys")))
    }

    @Test fun shortSessionsIgnored() {
        assertTrue(agg(r("a", 0), p("a", 999)).isEmpty())
        assertEquals(1000L, agg(r("a", 0), p("a", 1000))["a"])
    }

    @Test fun overlappingResumeEndsPrevious() {
        val m = agg(r("a", 0), r("b", 10 * min), p("b", 15 * min), p("a", 16 * min))
        assertEquals(10 * min, m["a"])
        assertEquals(5 * min, m["b"])
    }

    @Test fun unsortedInputIsSorted() {
        assertEquals(mapOf("a" to 10 * min), agg(p("a", 10 * min), r("a", 0)))
    }

    @Test fun duplicateResumeSamePackageKeepsOriginalStart() {
        assertEquals(mapOf("a" to 10 * min), agg(r("a", 0), r("a", 4 * min), p("a", 10 * min)))
    }

    @Test fun emptyEvents() {
        assertTrue(agg().isEmpty())
    }

    @Test fun lateNightShare() {
        val h = 3_600_000L
        // UTC, window = one day starting at 0: 23:00-24:00 counts, 10:00-11:00 does not
        val ev = listOf(r("a", 10 * h), p("a", 11 * h), r("a", 23 * h), p("a", 24 * h))
        val share = InsightsModel.lateNightShare(ev, 0, 24 * h, emptySet(), java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(0.5f, share, 0.001f)
        assertEquals(0f, InsightsModel.lateNightShare(emptyList(), 0, 24 * h, emptySet(), java.util.TimeZone.getTimeZone("UTC")), 0f)
    }
}
