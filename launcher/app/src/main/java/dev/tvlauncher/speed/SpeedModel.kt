package dev.tvlauncher.speed

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** What started a cleanup. */
enum class Trigger { MANUAL, HOME, AUTO }

/** Short sizes for the TV: "312 MB", "1.4 GB", "820 KB". Decimal units, like the TV's own storage page. */
object Bytes {
    const val KB = 1_000L
    const val MB = 1_000_000L
    const val GB = 1_000_000_000L

    fun short(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b >= 10 * GB -> "${b / GB} GB"
            b >= GB -> String.format(Locale.ROOT, "%.1f GB", b.toDouble() / GB)
            b >= MB -> "${b / MB} MB"
            b >= KB -> "${b / KB} KB"
            b == 0L -> "0 MB"
            else -> "1 KB"
        }
    }
}

/**
 * When cleanups may run. Never more than one at a time (the cleaner guards that), never in a
 * tight loop: each trigger has its own minimum gap since the last run of any kind, and the
 * unattended triggers share a daily cap.
 */
object RateLimit {
    const val MANUAL_GAP_MS = 15_000L
    const val HOME_GAP_MS = 10 * 60_000L
    const val AUTO_GAP_MS = 3 * 60 * 60_000L
    const val DAY_MS = 24 * 60 * 60_000L
    /** HOME and AUTO runs in any 24 hours. MANUAL is never capped (the owner pressed OK). */
    const val UNATTENDED_PER_DAY = 12
    /** How many past runs the prefs keep. */
    const val HISTORY = 24

    fun gap(t: Trigger): Long = when (t) {
        Trigger.MANUAL -> MANUAL_GAP_MS
        Trigger.HOME -> HOME_GAP_MS
        Trigger.AUTO -> AUTO_GAP_MS
    }

    /** [history] holds past run start times (any trigger) and [unattended] those of HOME/AUTO runs. */
    fun allows(t: Trigger, now: Long, history: List<Long>, unattended: List<Long>): Boolean {
        val last = history.filter { it <= now }.maxOrNull()
        // A clock that jumped backwards (last run "in the future") counts as a recent run.
        val future = history.any { it > now }
        if (future && t != Trigger.MANUAL) return false
        if (last != null && now - last < gap(t)) return false
        if (t != Trigger.MANUAL && unattended.count { now - it in 0 until DAY_MS } >= UNATTENDED_PER_DAY) return false
        return true
    }

    /** Appends [now] and keeps the newest [HISTORY] entries. */
    fun record(history: List<Long>, now: Long): List<Long> = (history + now).sortedDescending().take(HISTORY).sorted()
}

/** The automatic cleanup schedule: a quiet hour, every N days. Pure; time zone passed in. */
object SpeedSchedule {
    const val DEFAULT_HOUR = 4
    val EVERY_DAYS = listOf(1, 2, 3, 7)
    const val DEFAULT_EVERY = 1
    /** A run counts for "today" even when it happened a little earlier than exactly N days ago. */
    const val SLACK_MS = 3 * 60 * 60_000L
    /** The alarm is inexact: the system may deliver it anywhere in this window after the quiet hour. */
    const val WINDOW_MS = 60 * 60_000L

    fun normalizeHour(h: Int): Int = ((h % 24) + 24) % 24

    fun normalizeEvery(d: Int): Int = if (d in EVERY_DAYS) d else DEFAULT_EVERY

    /** The next [hour]:00 strictly after [now] in [zone]. */
    fun nextQuietTime(now: Long, hour: Int, zone: TimeZone): Long {
        val c = Calendar.getInstance(zone, Locale.ROOT)
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, normalizeHour(hour))
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Whether an automatic cleanup is due: never ran, or at least [everyDays] days (minus slack) ago. */
    fun isDue(now: Long, lastAutoRun: Long, everyDays: Int): Boolean {
        if (lastAutoRun <= 0L) return true
        if (lastAutoRun > now) return true // clock went backwards; the rate limit still guards
        return now - lastAutoRun >= normalizeEvery(everyDays) * RateLimit.DAY_MS - SLACK_MS
    }

    fun everyLabel(d: Int): String = if (d == 1) "Every night" else "Every $d days"

    fun hourLabel(h: Int): String = String.format(Locale.ROOT, "%02d:00", normalizeHour(h))
}

/** Why an automatic run did or did not start. */
sealed class AutoDecision {
    object Run : AutoDecision()
    data class Skip(val reason: Reason) : AutoDecision()

    enum class Reason { DISABLED, NOT_DUE, SCREEN_ON, PLAYING, RATE_LIMITED, BUSY }
}

object AutoPolicy {
    /**
     * The unattended nightly run. Only while the screen is off (the TV is idle) and nothing is
     * playing; if the quiet-hour alarm finds the screen on, the run waits for the next screen-off
     * or idle signal (it stays due).
     */
    fun decide(
        enabled: Boolean,
        now: Long,
        lastAutoRun: Long,
        everyDays: Int,
        screenOn: Boolean,
        playing: Boolean,
        rateOk: Boolean,
        running: Boolean
    ): AutoDecision = when {
        !enabled -> AutoDecision.Skip(AutoDecision.Reason.DISABLED)
        running -> AutoDecision.Skip(AutoDecision.Reason.BUSY)
        !SpeedSchedule.isDue(now, lastAutoRun, everyDays) -> AutoDecision.Skip(AutoDecision.Reason.NOT_DUE)
        screenOn -> AutoDecision.Skip(AutoDecision.Reason.SCREEN_ON)
        playing -> AutoDecision.Skip(AutoDecision.Reason.PLAYING)
        !rateOk -> AutoDecision.Skip(AutoDecision.Reason.RATE_LIMITED)
        else -> AutoDecision.Run
    }
}

/** "Free memory when you go Home": conservative by design. */
object HomePolicy {
    /** Wait this long on the home screen before closing anything (a quick launch cancels it). */
    const val SETTLE_MS = 4_000L
    /** Apps used within this window are left alone: you may be about to go back to them. */
    const val RECENT_MS = 30 * 60_000L

    fun shouldRun(enabled: Boolean, usageAccess: Boolean, playing: Boolean, rateOk: Boolean, running: Boolean): Boolean =
        enabled && usageAccess && !playing && rateOk && !running
}

/** Measurements around a cleanup; the result text never claims more than was measured. */
data class Snapshot(val storageFree: Long, val memAvail: Long)

data class CleanupResult(
    val trigger: Trigger,
    val storageFreed: Long,
    val memoryFreed: Long,
    val ownCacheBytes: Long,
    /** Apps the system was asked to close; -1 when asked without usage access (count unknown). */
    val appsClosed: Int,
    /** Background closing was skipped (something playing, nothing allowed). */
    val closingSkipped: Boolean,
    val at: Long
)

object FreedBytes {
    /**
     * Storage freed: the measured rise in free space, but at least what we deleted ourselves
     * (another app writing meanwhile must not hide our own deletions), never negative.
     * Memory freed: the measured rise in available memory, never negative.
     */
    fun storage(before: Snapshot, after: Snapshot, ownDeleted: Long): Long =
        maxOf(after.storageFree - before.storageFree, ownDeleted.coerceAtLeast(0L), 0L)

    fun memory(before: Snapshot, after: Snapshot): Long = (after.memAvail - before.memAvail).coerceAtLeast(0L)

    /** Below this a change is noise (the TV's own allocations move by a few MB all the time). */
    const val NOISE = 2 * Bytes.MB

    /** The row value: "Freed 312 MB", "Freed 180 MB of memory", "Already tidy". */
    fun headline(r: CleanupResult): String = when {
        r.storageFreed >= NOISE -> "Freed ${Bytes.short(r.storageFreed)}"
        r.memoryFreed >= NOISE -> "Freed ${Bytes.short(r.memoryFreed)} of memory"
        else -> "Already tidy"
    }

    /** One plain line under it. */
    fun detail(r: CleanupResult): String {
        val parts = ArrayList<String>()
        if (r.storageFreed >= NOISE && r.memoryFreed >= NOISE) parts += "${Bytes.short(r.memoryFreed)} more memory"
        parts += when {
            r.closingSkipped -> "Background apps left open while something plays"
            r.appsClosed < 0 -> "Background apps closed"
            r.appsClosed == 1 -> "1 background app closed"
            r.appsClosed > 1 -> "${r.appsClosed} background apps closed"
            else -> "No background apps to close"
        }
        return parts.joinToString(" · ")
    }
}

/** The read-only "Why is my TV slow?" row. */
object SlowHint {
    data class Facts(
        val memAvail: Long,
        val memTotal: Long,
        val lowMemory: Boolean,
        val storageFree: Long,
        val storageTotal: Long,
        /** Apps used today that may still sit in memory; null without usage access. */
        val backgroundApps: Int?
    )

    /** Storage below this (or 10%) slows app installs and updates down noticeably. */
    const val LOW_STORAGE = 1 * Bytes.GB
    const val LOW_MEMORY_FRACTION = 0.15
    const val MANY_BACKGROUND = 5

    fun value(f: Facts): String {
        val memLow = f.lowMemory || (f.memTotal > 0 && f.memAvail.toDouble() / f.memTotal < LOW_MEMORY_FRACTION)
        val storageLow = f.storageTotal > 0 && (f.storageFree < LOW_STORAGE || f.storageFree.toDouble() / f.storageTotal < 0.10)
        return when {
            storageLow -> "Storage almost full"
            memLow -> "Memory is tight"
            (f.backgroundApps ?: 0) >= MANY_BACKGROUND -> "Many apps open"
            else -> "Looks fine"
        }
    }

    fun subtitle(f: Facts): String {
        val parts = ArrayList<String>()
        parts += "Memory ${Bytes.short(f.memAvail)} free of ${Bytes.short(f.memTotal)}"
        parts += "Storage ${Bytes.short(f.storageFree)} free"
        f.backgroundApps?.let { parts += if (it == 1) "1 app in the background" else "$it apps in the background" }
        return parts.joinToString(" · ")
    }
}
