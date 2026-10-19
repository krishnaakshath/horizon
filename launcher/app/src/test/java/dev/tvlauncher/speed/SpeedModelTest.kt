package dev.tvlauncher.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpeedModelTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = RateLimit.DAY_MS
    private val hour = 60 * 60_000L

    private fun utcMillis(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    // Bytes

    @Test fun bytes_shortFormat() {
        assertEquals("312 MB", Bytes.short(312 * Bytes.MB + 400_000))
        assertEquals("1.4 GB", Bytes.short(1_400_000_000L))
        assertEquals("12 GB", Bytes.short(12_300_000_000L))
        assertEquals("820 KB", Bytes.short(820_000L))
        assertEquals("0 MB", Bytes.short(0L))
        assertEquals("0 MB", Bytes.short(-5L))
        assertEquals("1 KB", Bytes.short(10L))
    }

    // Rate limiting

    @Test fun rate_firstRunAllowedForEveryTrigger() {
        Trigger.values().forEach { assertTrue(it.name, RateLimit.allows(it, 1_000_000L, emptyList(), emptyList())) }
    }

    @Test fun rate_manualNeedsOnlyAShortGap() {
        val now = 10 * day
        assertFalse(RateLimit.allows(Trigger.MANUAL, now, listOf(now - 5_000), emptyList()))
        assertTrue(RateLimit.allows(Trigger.MANUAL, now, listOf(now - RateLimit.MANUAL_GAP_MS), emptyList()))
    }

    @Test fun rate_homeWaitsTenMinutesAfterAnyRun() {
        val now = 10 * day
        assertFalse(RateLimit.allows(Trigger.HOME, now, listOf(now - 9 * 60_000L), emptyList()))
        assertTrue(RateLimit.allows(Trigger.HOME, now, listOf(now - 10 * 60_000L), emptyList()))
    }

    @Test fun rate_autoWaitsHours() {
        val now = 10 * day
        assertFalse(RateLimit.allows(Trigger.AUTO, now, listOf(now - 2 * hour), emptyList()))
        assertTrue(RateLimit.allows(Trigger.AUTO, now, listOf(now - 3 * hour), emptyList()))
    }

    @Test fun rate_unattendedDailyCap_butManualIsNeverCapped() {
        val now = 10 * day
        val runs = (1..RateLimit.UNATTENDED_PER_DAY).map { now - it * 61 * 60_000L }
        assertFalse(RateLimit.allows(Trigger.HOME, now, runs, runs))
        assertTrue(RateLimit.allows(Trigger.MANUAL, now, runs, runs))
        // Runs older than a day no longer count.
        val old = runs.map { it - day }
        assertTrue(RateLimit.allows(Trigger.HOME, now, old, old))
    }

    @Test fun rate_clockJumpedBack_blocksUnattendedRuns() {
        val now = 10 * day
        val future = listOf(now + hour)
        assertFalse(RateLimit.allows(Trigger.AUTO, now, future, future))
        assertFalse(RateLimit.allows(Trigger.HOME, now, future, future))
        assertTrue(RateLimit.allows(Trigger.MANUAL, now, future, future))
    }

    @Test fun rate_recordKeepsNewestEntriesSorted() {
        var h = emptyList<Long>()
        for (i in 1..40) h = RateLimit.record(h, i.toLong())
        assertEquals(RateLimit.HISTORY, h.size)
        assertEquals(40L, h.last())
        assertEquals(h.sorted(), h)
    }

    // Schedule

    @Test fun schedule_nextQuietHour_laterToday() {
        val now = utcMillis(2026, 10, 20, 1, 30)
        assertEquals(utcMillis(2026, 10, 20, 4), SpeedSchedule.nextQuietTime(now, 4, utc))
    }

    @Test fun schedule_nextQuietHour_tomorrowWhenPassedOrExact() {
        assertEquals(utcMillis(2026, 10, 21, 4), SpeedSchedule.nextQuietTime(utcMillis(2026, 10, 20, 4), 4, utc))
        assertEquals(utcMillis(2026, 10, 21, 4), SpeedSchedule.nextQuietTime(utcMillis(2026, 10, 20, 22), 4, utc))
    }

    @Test fun schedule_hourIsNormalized() {
        assertEquals(utcMillis(2026, 10, 21, 1), SpeedSchedule.nextQuietTime(utcMillis(2026, 10, 20, 22), 25, utc))
        assertEquals(23, SpeedSchedule.normalizeHour(-1))
    }

    @Test fun schedule_isDue() {
        val now = 100 * day
        assertTrue(SpeedSchedule.isDue(now, 0L, 1))
        assertTrue(SpeedSchedule.isDue(now, now - day, 1))
        // Last night's run was a little later than tonight's alarm: still due (slack).
        assertTrue(SpeedSchedule.isDue(now, now - day + 2 * hour, 1))
        assertFalse(SpeedSchedule.isDue(now, now - 12 * hour, 1))
        assertFalse(SpeedSchedule.isDue(now, now - 2 * day, 3))
        assertTrue(SpeedSchedule.isDue(now, now - 3 * day, 3))
        assertTrue(SpeedSchedule.isDue(now, now + day, 1)) // clock went backwards
    }

    @Test fun schedule_unknownIntervalFallsBackToNightly() {
        assertEquals(1, SpeedSchedule.normalizeEvery(5))
        assertEquals(7, SpeedSchedule.normalizeEvery(7))
        assertEquals("Every night", SpeedSchedule.everyLabel(1))
        assertEquals("Every 3 days", SpeedSchedule.everyLabel(3))
        assertEquals("04:00", SpeedSchedule.hourLabel(4))
    }

    // Auto and Home policies

    private fun auto(
        enabled: Boolean = true, last: Long = 0L, screenOn: Boolean = false, playing: Boolean = false,
        rateOk: Boolean = true, running: Boolean = false
    ) = AutoPolicy.decide(enabled, 100 * day, last, 1, screenOn, playing, rateOk, running)

    private fun reason(d: AutoDecision) = (d as AutoDecision.Skip).reason

    @Test fun auto_runsWhenDueAndIdle() {
        assertEquals(AutoDecision.Run, auto())
    }

    @Test fun auto_skipReasons() {
        assertEquals(AutoDecision.Reason.DISABLED, reason(auto(enabled = false)))
        assertEquals(AutoDecision.Reason.NOT_DUE, reason(auto(last = 100 * day - hour)))
        assertEquals(AutoDecision.Reason.SCREEN_ON, reason(auto(screenOn = true)))
        assertEquals(AutoDecision.Reason.PLAYING, reason(auto(playing = true)))
        assertEquals(AutoDecision.Reason.RATE_LIMITED, reason(auto(rateOk = false)))
        assertEquals(AutoDecision.Reason.BUSY, reason(auto(running = true)))
    }

    @Test fun home_isConservative() {
        assertTrue(HomePolicy.shouldRun(enabled = true, usageAccess = true, playing = false, rateOk = true, running = false))
        assertFalse(HomePolicy.shouldRun(enabled = false, usageAccess = true, playing = false, rateOk = true, running = false))
        assertFalse(HomePolicy.shouldRun(enabled = true, usageAccess = false, playing = false, rateOk = true, running = false))
        assertFalse(HomePolicy.shouldRun(enabled = true, usageAccess = true, playing = true, rateOk = true, running = false))
        assertFalse(HomePolicy.shouldRun(enabled = true, usageAccess = true, playing = false, rateOk = false, running = false))
        assertFalse(HomePolicy.shouldRun(enabled = true, usageAccess = true, playing = false, rateOk = true, running = true))
    }

    // Freed-bytes accounting

    private fun result(storage: Long, memory: Long, closed: Int = 3, skipped: Boolean = false) =
        CleanupResult(Trigger.MANUAL, storage, memory, 0L, closed, skipped, 0L)

    @Test fun freed_storageIsMeasuredDelta() {
        val b = Snapshot(1_000 * Bytes.MB, 200 * Bytes.MB)
        val a = Snapshot(1_312 * Bytes.MB, 380 * Bytes.MB)
        assertEquals(312 * Bytes.MB, FreedBytes.storage(b, a, 10 * Bytes.MB))
        assertEquals(180 * Bytes.MB, FreedBytes.memory(b, a))
    }

    @Test fun freed_neverNegative_andOwnDeletionsCount() {
        val b = Snapshot(1_000 * Bytes.MB, 300 * Bytes.MB)
        val a = Snapshot(990 * Bytes.MB, 250 * Bytes.MB) // something else wrote 10 MB and allocated memory
        assertEquals(5 * Bytes.MB, FreedBytes.storage(b, a, 5 * Bytes.MB))
        assertEquals(0L, FreedBytes.storage(b, a, 0L))
        assertEquals(0L, FreedBytes.memory(b, a))
        assertEquals(0L, FreedBytes.storage(b, a, -7L))
    }

    @Test fun freed_headline() {
        assertEquals("Freed 312 MB", FreedBytes.headline(result(312 * Bytes.MB, 180 * Bytes.MB)))
        assertEquals("Freed 180 MB of memory", FreedBytes.headline(result(1 * Bytes.MB, 180 * Bytes.MB)))
        assertEquals("Already tidy", FreedBytes.headline(result(1 * Bytes.MB, 1 * Bytes.MB)))
    }

    @Test fun freed_detail() {
        assertEquals("180 MB more memory · 3 background apps closed", FreedBytes.detail(result(312 * Bytes.MB, 180 * Bytes.MB)))
        assertEquals("1 background app closed", FreedBytes.detail(result(0, 0, closed = 1)))
        assertEquals("No background apps to close", FreedBytes.detail(result(0, 0, closed = 0)))
        assertEquals("Background apps closed", FreedBytes.detail(result(0, 0, closed = -1)))
        assertEquals("Background apps left open while something plays", FreedBytes.detail(result(0, 0, closed = 0, skipped = true)))
    }

    // Hint row

    private fun facts(memAvail: Long = 400 * Bytes.MB, low: Boolean = false, free: Long = 3 * Bytes.GB, bg: Int? = 2) =
        SlowHint.Facts(memAvail, 1_000 * Bytes.MB, low, free, 8 * Bytes.GB, bg)

    @Test fun hint_values() {
        assertEquals("Looks fine", SlowHint.value(facts()))
        assertEquals("Memory is tight", SlowHint.value(facts(memAvail = 100 * Bytes.MB)))
        assertEquals("Memory is tight", SlowHint.value(facts(low = true)))
        assertEquals("Storage almost full", SlowHint.value(facts(free = 500 * Bytes.MB, memAvail = 50 * Bytes.MB)))
        assertEquals("Many apps open", SlowHint.value(facts(bg = 6)))
        assertEquals("Looks fine", SlowHint.value(facts(bg = null)))
    }

    @Test fun hint_subtitle_withAndWithoutUsageAccess() {
        assertEquals("Memory 400 MB free of 1.0 GB · Storage 3.0 GB free · 2 apps in the background", SlowHint.subtitle(facts()))
        assertEquals("Memory 400 MB free of 1.0 GB · Storage 3.0 GB free", SlowHint.subtitle(facts(bg = null)))
        assertTrue(SlowHint.subtitle(facts(bg = 1)).endsWith("1 app in the background"))
    }

    @Test fun text_lastLineAndAuto() {
        assertEquals("Nothing yet", SpeedText.lastLine(null) { "x" })
        val r = CleanupResult(Trigger.AUTO, 312 * Bytes.MB, 0, 0, 0, false, 5L)
        assertEquals("Freed 312 MB overnight at 03:58", SpeedText.lastLine(r) { "03:58" })
        assertEquals("Off", SpeedText.autoSubtitle(false, 4, 1))
        assertEquals("Every night around 04:00 while the TV is off", SpeedText.autoSubtitle(true, 4, 1))
    }

    @Test fun text_hasNoDeveloperWords() {
        val all = listOf(SpeedText.AUTO_SUBTITLE, SpeedText.HOME_SUBTITLE, SpeedText.HOME_NEEDS_ACCESS, SpeedText.CLEAN_SUBTITLE) +
            SpeedCleaner.Step.values().map { it.label } + KeepRules.Reason.values().map { it.label }
        for (s in all) for (w in listOf("adb", "shell", "appops", "permission", "kill", "process")) {
            assertFalse("$s contains $w", s.lowercase().contains(w))
        }
    }
}
