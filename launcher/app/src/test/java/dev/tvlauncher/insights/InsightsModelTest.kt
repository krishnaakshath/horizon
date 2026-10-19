package dev.tvlauncher.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsModelTest {
    private val m = 60_000L
    private val h = 60 * m
    private fun day(start: Long = 0, vararg u: Pair<String, Long>) = DayUsage(start, mapOf(*u))
    private val label: (String) -> String = { it.uppercase() }

    @Test fun totalIsSum() = assertEquals(30 * m, day(0, "a" to 10 * m, "b" to 20 * m).totalMs)

    @Test fun topAppsSortedAndLimited() {
        val d = day(0, "a" to 1 * m, "b" to 3 * m, "c" to 2 * m)
        assertEquals(listOf("b" to 3 * m, "c" to 2 * m), InsightsModel.topApps(d, 2))
        assertEquals(emptyList<Pair<String, Long>>(), InsightsModel.topApps(day(0), 5))
    }

    @Test fun topAppsTiesBreakByPackageName() {
        val d = day(0, "z" to 5 * m, "a" to 5 * m, "m" to 5 * m)
        assertEquals(listOf("a", "m", "z"), InsightsModel.topApps(d, 5).map { it.first })
    }

    @Test fun weekTotalsInDayOrder() {
        val days = listOf(day(2, "a" to 3 * m), day(1, "a" to 2 * m), day(3))
        assertEquals(listOf(2 * m, 3 * m, 0L), InsightsModel.weekTotals(days))
    }

    @Test fun delta() {
        assertEquals(20, InsightsModel.deltaVsYesterdayPercent(day(0, "a" to 12 * m), day(0, "a" to 10 * m)))
        assertEquals(-50, InsightsModel.deltaVsYesterdayPercent(day(0, "a" to 5 * m), day(0, "a" to 10 * m)))
        assertNull(InsightsModel.deltaVsYesterdayPercent(day(0, "a" to 5 * m), day(0)))
        assertNull(InsightsModel.deltaVsYesterdayPercent(day(0, "a" to 5 * m), null))
    }

    @Test fun formatDuration() {
        assertEquals("0m", SummaryText.formatDuration(0))
        assertEquals("<1m", SummaryText.formatDuration(59_000))
        assertEquals("1m", SummaryText.formatDuration(60_000))
        assertEquals("45m", SummaryText.formatDuration(45 * m))
        assertEquals("1h 01m", SummaryText.formatDuration(61 * m))
        assertEquals("2h", SummaryText.formatDuration(2 * h))
        assertEquals("25h", SummaryText.formatDuration(25 * h))
    }

    @Test fun formatClock() {
        assertEquals("2h 05m", SummaryText.formatClock(2 * h + 5 * m))
        assertEquals("0h 00m", SummaryText.formatClock(0))
        assertEquals("0h 42m", SummaryText.formatClock(42 * m))
    }

    private val now = 20 * h // well into the day

    @Test fun emptyDayIsQuiet() =
        assertEquals("Quiet day so far.", SummaryText.build(day(0), null, label, now))

    @Test fun tinyDayIsQuiet() =
        assertEquals("Quiet day so far.", SummaryText.build(day(0, "a" to 2 * m), day(0, "a" to 1 * h), label, now))

    @Test fun dominantApp() {
        val t = day(0, "yt" to 72 * m, "nf" to 53 * m)
        assertEquals("Most of today went to YT — 1h 12m of 2h 05m.", SummaryText.build(t, null, label, now))
    }

    @Test fun singleAppIsDominant() {
        assertEquals("Most of today went to A — 30m of 30m.", SummaryText.build(day(0, "a" to 30 * m), null, label, now))
    }

    @Test fun spreadOutDay() {
        val t = day(0, "a" to 30 * m, "b" to 30 * m, "c" to 30 * m)
        assertEquals("A led today — 30m of 1h 30m.", SummaryText.build(t, null, label, now))
    }

    @Test fun tieUsesAlphabeticalPackage() {
        val t = day(0, "b" to 30 * m, "a" to 30 * m)
        assertEquals("A led today — 30m of 1h.", SummaryText.build(t, null, label, now))
    }

    @Test fun upAndDown() {
        val y = day(0, "a" to 100 * m)
        assertEquals("Most of today went to A — 2h of 2h.\nUp 20% vs yesterday.",
            SummaryText.build(day(0, "a" to 120 * m), y, label, now))
        assertEquals("Most of today went to A — 1h of 1h.\nDown 40% vs yesterday.",
            SummaryText.build(day(0, "a" to 60 * m), y, label, now))
    }

    @Test fun similarToYesterday() {
        assertEquals("Most of today went to A — 1h 45m of 1h 45m.\nAbout the same as yesterday.",
            SummaryText.build(day(0, "a" to 105 * m), day(0, "a" to 100 * m), label, now))
    }

    @Test fun earlyInDayNoComparison() {
        val t = day(1_000_000, "a" to 30 * m)
        assertEquals("Most of today went to A — 30m of 30m.",
            SummaryText.build(t, day(0, "a" to 100 * m), label, 1_000_000 + 2 * h))
    }

    @Test fun deltaVsYesterday_comparesSameTimeOfDay() {
        val ys = 0L
        val ev = listOf(UsageEvent("a", UsageEvent.Type.RESUMED, 8 * h), UsageEvent("a", UsageEvent.Type.PAUSED, 9 * h),
            UsageEvent("a", UsageEvent.Type.RESUMED, 20 * h), UsageEvent("a", UsageEvent.Type.PAUSED, 23 * h))
        val y = InsightsModel.sameTimeYesterday(ev, ys, 10 * h, emptySet())
        assertEquals(1 * h, y.totalMs)
        val today = DayUsage(24 * h, mapOf("a" to 90 * m))
        assertEquals("Most of today went to A \u2014 1h 30m of 1h 30m.\nUp 50% vs yesterday.",
            SummaryText.build(today, y, label, 24 * h + 10 * h))
    }

    @Test fun delta_omitted_whenYesterdayBaselineTooSmall() {
        val today = day(0, "a" to 60 * m)
        assertEquals("Most of today went to A \u2014 1h of 1h.",
            SummaryText.build(today, day(0, "a" to 9 * m), label, now))
    }

    @Test fun delta_omitted_before3hElapsed() {
        val today = day(0, "a" to 60 * m)
        assertEquals("Most of today went to A \u2014 1h of 1h.",
            SummaryText.build(today, day(0, "a" to 90 * m), label, 2 * h))
    }

    @Test fun lateNightLine() {
        val t = day(0, "a" to 60 * m)
        assertEquals("Most of today went to A \u2014 1h of 1h.\nMostly late-night viewing today.",
            SummaryText.build(t, null, label, now, lateNightShare = 0.4f))
        assertEquals("Most of today went to A \u2014 1h of 1h.", SummaryText.build(t, null, label, now, lateNightShare = 0.39f))
        assertEquals("Most of today went to A \u2014 20m of 20m.",
            SummaryText.build(day(0, "a" to 20 * m), null, label, now, lateNightShare = 1f))
    }

    @Test fun dayBounds_dstSpringForward_london() {
        val tz = java.util.TimeZone.getTimeZone("Europe/London")
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(utc).apply { clear(); set(2026, 2, 30, 12, 0, 0) } // 30 Mar noon UTC (BST)
        val now = cal.timeInMillis
        val s = DayBounds.starts(now, 3, tz)
        fun utcMs(y: Int, mo: Int, d: Int, hh: Int) = java.util.Calendar.getInstance(utc).apply { clear(); set(y, mo, d, hh, 0, 0) }.timeInMillis
        assertEquals(listOf(utcMs(2026, 2, 28, 0), utcMs(2026, 2, 29, 0), utcMs(2026, 2, 29, 23)), s)
        assertEquals(23 * h, s[2] - s[1]) // the short day
    }
}
