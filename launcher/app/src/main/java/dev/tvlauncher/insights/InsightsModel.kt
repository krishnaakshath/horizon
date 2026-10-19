package dev.tvlauncher.insights

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

data class DayUsage(val dayStartMs: Long, val perApp: Map<String, Long>) {
    val totalMs: Long get() = perApp.values.sum()
}

/** Local-midnight day boundaries, DST safe (each start is set from calendar fields, not offset by 24h). */
object DayBounds {
    /** Start of the local day containing [nowMs] minus [daysAgo] days. */
    fun dayStart(nowMs: Long, daysAgo: Int, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = nowMs
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** [count] day starts, oldest first, ending with today. */
    fun starts(nowMs: Long, count: Int, tz: TimeZone): List<Long> =
        (count - 1 downTo 0).map { dayStart(nowMs, it, tz) }
}

object InsightsModel {
    /** Yesterday's usage up to the same time of day as [elapsedTodayMs] into today. */
    fun sameTimeYesterday(events: List<UsageEvent>, yesterdayStartMs: Long, elapsedTodayMs: Long, ignore: Set<String>): DayUsage =
        DayUsage(yesterdayStartMs, UsageAggregator.aggregate(events, yesterdayStartMs, yesterdayStartMs + elapsedTodayMs, ignore))

    const val LATE_START_HOUR = 23
    const val LATE_END_HOUR = 5

    /** Largest first; ties by package name so output is deterministic. */
    fun topApps(day: DayUsage, n: Int): List<Pair<String, Long>> =
        day.perApp.entries.filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(n).map { it.key to it.value }

    /** Totals in chronological order. */
    fun weekTotals(days: List<DayUsage>): List<Long> = days.sortedBy { it.dayStartMs }.map { it.totalMs }

    /** Rounded percent change; null when there is no baseline. */
    fun deltaVsYesterdayPercent(today: DayUsage, yesterday: DayUsage?): Int? {
        val y = yesterday?.totalMs ?: return null
        if (y <= 0L) return null
        return ((today.totalMs - y) * 100.0 / y).roundToInt()
    }

    /** Fraction (0..1) of watch time falling between 23:00 and 05:00 local time. */
    fun lateNightShare(events: List<UsageEvent>, fromMs: Long, toMs: Long, ignore: Set<String>, tz: TimeZone): Float {
        val sessions = UsageAggregator.sessions(events, fromMs, toMs, ignore)
        val total = sessions.sumOf { it.endMs - it.startMs }
        if (total <= 0L) return 0f
        var late = 0L
        val cal = Calendar.getInstance(tz)
        for (s in sessions) {
            var t = s.startMs
            while (t < s.endMs) {
                cal.timeInMillis = t
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.HOUR_OF_DAY, 1)
                val next = minOf(cal.timeInMillis, s.endMs)
                if (hour >= LATE_START_HOUR || hour < LATE_END_HOUR) late += next - t
                t = next
            }
        }
        return late.toFloat() / total
    }
}

/** Plain-language, rule-based sentences. Deterministic: same input, same text. */
object SummaryText {
    private const val QUIET_MS = 5 * 60_000L
    private const val SAME_PCT = 10
    private const val MIN_ELAPSED_FOR_COMPARE_MS = 3 * 3_600_000L
    private const val MIN_BASELINE_MS = 10 * 60_000L
    private const val LATE_SHARE = 0.4f
    private const val LATE_MIN_TOTAL_MS = 30 * 60_000L

    /** "<1m", "45m", "1h 1m", "2h", "25h". */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMin = ms / 60_000
        if (totalMin < 1) return "<1m"
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h %02dm".format(m)
        }
    }

    /** Big-number form: "2h 05m". */
    fun formatClock(ms: Long): String {
        val totalMin = (ms.coerceAtLeast(0)) / 60_000
        return "${totalMin / 60}h %02dm".format(totalMin % 60)
    }

    /**
     * [yesterday] must already be clipped to the same time of day (see [InsightsModel.sameTimeYesterday]).
     * [lateNightShare] is the 0..1 share of today's viewing between 23:00 and 05:00.
     */
    fun build(
        today: DayUsage, yesterday: DayUsage?, labelOf: (String) -> String, nowMs: Long,
        lateNightShare: Float = 0f
    ): String {
        val total = today.totalMs
        if (total < QUIET_MS) return "Quiet day so far."
        val (topPkg, topMs) = InsightsModel.topApps(today, 1).first()
        val name = labelOf(topPkg)
        val amounts = "${formatDuration(topMs)} of ${formatDuration(total)}"
        val first = if (topMs * 2 > total) "Most of today went to $name — $amounts."
        else "$name led today — $amounts."

        val canCompare = yesterday != null && yesterday.totalMs >= MIN_BASELINE_MS &&
            nowMs - today.dayStartMs >= MIN_ELAPSED_FOR_COMPARE_MS
        val second = if (!canCompare) null else {
            val d = InsightsModel.deltaVsYesterdayPercent(today, yesterday)
            when {
                d == null -> null
                d >= SAME_PCT -> "Up $d% vs yesterday."
                d <= -SAME_PCT -> "Down ${-d}% vs yesterday."
                else -> "About the same as yesterday."
            }
        }
        val late = if (lateNightShare >= LATE_SHARE && total >= LATE_MIN_TOTAL_MS) "Mostly late-night viewing today." else null
        return listOfNotNull(first, late, second).joinToString("\n")
    }
}
