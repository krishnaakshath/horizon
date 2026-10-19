package dev.tvlauncher.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RoutineModelTest {
    private fun at(tz: TimeZone, s: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = tz }.parse(s)!!.time
    private fun fmt(tz: TimeZone, t: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US).apply { timeZone = tz }.format(t)

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Test fun weekdays_skipTheWeekend() {
        val r = RoutineModel.defaultRoutine(1) // weekdays 07:00
        // Friday 2026-10-23 08:00 -> Monday 2026-10-26 07:00
        assertEquals("2026-10-26 07:00 +0100", fmt(berlin, RoutineModel.nextFire(r, at(berlin, "2026-10-23 08:00"), berlin)!!))
        assertEquals("2026-10-23 07:00 +0200", fmt(berlin, RoutineModel.nextFire(r, at(berlin, "2026-10-23 06:59"), berlin)!!))
    }

    @Test fun springForwardGap_movesForward_fallBack_firesOnce() {
        val r = Routine(1, true, 2 * 60 + 30, RoutineModel.EVERY_DAY, RoutineAction.Ambient)
        assertEquals("2026-03-29 03:30 +0200", fmt(berlin, RoutineModel.nextFire(r, at(berlin, "2026-03-29 00:00"), berlin)!!))
        val first = RoutineModel.nextFire(r, at(berlin, "2026-10-25 00:00"), berlin)!!
        assertEquals("2026-10-25 02:30 +0200", fmt(berlin, first))
        // After the first 02:30 the next one is the following day, not the repeated hour.
        assertEquals("2026-10-26 02:30 +0100", fmt(berlin, RoutineModel.nextFire(r, first, berlin)!!))
    }

    @Test fun otherZones_offAndEmpty() {
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
        val r = Routine(2, true, 22 * 60 + 30, RoutineModel.EVERY_DAY, RoutineAction.BedtimeOn)
        assertEquals("2026-10-20 22:30 +0530", fmt(kolkata, RoutineModel.nextFire(r, at(kolkata, "2026-10-20 12:00"), kolkata)!!))
        assertNull(RoutineModel.nextFire(r.copy(enabled = false), 0L, kolkata))
        assertNull(RoutineModel.nextFire(r.copy(days = emptySet()), 0L, kolkata))
    }

    @Test fun soonest_lateness_json_limits() {
        val a = Routine(1, true, 7 * 60, RoutineModel.EVERY_DAY, RoutineAction.Ambient)
        val b = Routine(2, true, 7 * 60, RoutineModel.EVERY_DAY, RoutineAction.Room("CINEMA"))
        val (t, ids) = RoutineModel.soonest(listOf(a, b), at(berlin, "2026-10-20 06:00"), berlin)!!
        assertEquals(listOf(1, 2), ids)
        assertTrue(t > 0)
        assertEquals(RoutineModel.Decision.SKIP_STANDBY, RoutineModel.decide(RoutineAction.Kind.AMBIENT, 0, screenOn = false))
        assertEquals(RoutineModel.Decision.SKIP_LATE, RoutineModel.decide(RoutineAction.Kind.AMBIENT, 60 * 60_000L, true))
        assertEquals(RoutineModel.Decision.RUN, RoutineModel.decide(RoutineAction.Kind.ROOM, 60 * 60_000L, false))
        val list = listOf(a, b, Routine(3, false, 0, setOf(6), RoutineAction.Launch("com.x.y", "X")))
        assertEquals(list, RoutineModel.fromJson(RoutineModel.toJson(list)))
        assertEquals(null, RoutineModel.nextId((1..5).map { RoutineModel.defaultRoutine(it) }))
        assertEquals("Weekdays 07:00 · Open ambient (clock and weather)", RoutineModel.summary(RoutineModel.defaultRoutine(1)))
    }

    @Test fun bedtimeAndSleepEnd() {
        assertTrue(BedtimeModel.isActive(1000L, 2000L))
        assertTrue(!BedtimeModel.isActive(1000L, 1000L + BedtimeModel.MAX_MS))
        assertEquals(5, BedtimeModel.idleDuring(10))
        assertEquals(2, BedtimeModel.idleDuring(2))
        assertEquals(listOf(SleepEndPlan.Step.AMBIENT, SleepEndPlan.Step.POWER_MENU), SleepEndPlan.steps(true))
        assertEquals(listOf(SleepEndPlan.Step.AMBIENT, SleepEndPlan.Step.MUTE), SleepEndPlan.steps(false))
    }
}
