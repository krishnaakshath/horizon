package dev.tvlauncher.room

import dev.tvlauncher.room.RoomMode.BRIGHT
import dev.tvlauncher.room.RoomMode.CINEMA
import dev.tvlauncher.room.RoomMode.EVENING
import dev.tvlauncher.room.RoomMode.STANDARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLightModelTest {
    private fun hm(h: Int, m: Int) = h * 60 + m

    private fun sched(min: Int, sunrise: Int? = null, sunset: Int? = null, schedule: Schedule = Schedule.DEFAULT) =
        RoomLightModel.scheduleMode(min, sunrise, sunset, schedule)

    private fun inputs(lux: Float?, now: Long, choice: RoomChoice = RoomChoice.AUTO, min: Int = hm(12, 0)) =
        RoomLightModel.Inputs(lux, min, null, null, choice, now)

    // ---- No-sun table (controller ruling) ----
    // CINEMA 23:30-06:30 | EVENING 06:30-07:30 | STANDARD 07:30-18:30 | EVENING 18:30-23:30

    @Test fun no_sun_table_boundaries() {
        assertEquals(CINEMA, sched(hm(6, 29)))
        assertEquals(EVENING, sched(hm(6, 30)))
        assertEquals(EVENING, sched(hm(7, 29)))
        assertEquals(STANDARD, sched(hm(7, 30)))
        assertEquals(STANDARD, sched(hm(12, 0)))
        assertEquals(STANDARD, sched(hm(18, 29)))
        assertEquals(EVENING, sched(hm(18, 30)))
        assertEquals(EVENING, sched(hm(23, 29)))
        assertEquals(CINEMA, sched(hm(23, 30)))
        assertEquals(CINEMA, sched(0))
        assertEquals(CINEMA, sched(1439))
    }

    @Test fun out_of_range_minutes_wrap() {
        assertEquals(sched(hm(12, 0)), sched(hm(12, 0) + 1440))
        assertEquals(sched(hm(23, 50)), sched(-10))
        assertEquals(CINEMA, sched(-1))
    }

    @Test fun user_overrides_respected_with_midnight_wrap() {
        // Day 08:00, evening 20:00, dark room from 01:00 (after midnight); morning EVENING = 07:00-08:00.
        val s = Schedule(dayStart = hm(8, 0), eveningStart = hm(20, 0), nightStart = hm(1, 0))
        assertEquals(STANDARD, sched(hm(8, 0), schedule = s))
        assertEquals(STANDARD, sched(hm(19, 59), schedule = s))
        assertEquals(EVENING, sched(hm(20, 0), schedule = s))
        assertEquals(EVENING, sched(hm(23, 59), schedule = s))
        assertEquals(EVENING, sched(hm(0, 30), schedule = s))
        assertEquals(CINEMA, sched(hm(1, 0), schedule = s))
        assertEquals(CINEMA, sched(hm(6, 59), schedule = s))
        assertEquals(EVENING, sched(hm(7, 0), schedule = s))
        assertEquals(EVENING, sched(hm(7, 59), schedule = s))
    }

    @Test fun insane_overrides_fall_back_to_defaults() {
        val bad = listOf(
            Schedule(dayStart = 600, eveningStart = 600, nightStart = 600),          // degenerate
            Schedule(dayStart = hm(7, 0), eveningStart = hm(18, 30), nightStart = hm(6, 45)), // ~23.75 h "night" (the 1425-min case)
            Schedule(dayStart = hm(7, 30), eveningStart = hm(18, 30), nightStart = hm(5, 0)), // night 1.5 h < 3 h
            Schedule(dayStart = hm(12, 0), eveningStart = hm(12, 30), nightStart = hm(15, 0)), // night 20 h > 12 h
            Schedule(dayStart = 1500, eveningStart = hm(18, 30), nightStart = hm(23, 30)),   // out of range
            Schedule(dayStart = hm(7, 30), eveningStart = -1, nightStart = hm(23, 30)),
            Schedule(dayStart = hm(7, 30), eveningStart = hm(23, 45), nightStart = hm(23, 30)) // evening after night
        )
        for (s in bad) {
            assertTrue(RoomLightModel.sane(s) == Schedule.DEFAULT)
            for (m in 0 until 1440) assertEquals("$s @$m", sched(m), sched(m, schedule = s))
        }
        // Night windows of exactly 3 h and 12 h are accepted.
        assertEquals(Schedule(hm(7, 30), hm(18, 30), hm(3, 30)), RoomLightModel.sane(Schedule(hm(7, 30), hm(18, 30), hm(3, 30))))
        assertEquals(Schedule(hm(7, 30), hm(17, 0), hm(18, 30)), RoomLightModel.sane(Schedule(hm(7, 30), hm(17, 0), hm(18, 30))))
    }

    // ---- Sun table (controller ruling) ----
    // EVENING [D-30, cinemaStart) with cinemaStart = max(D+90, 20:30)
    // CINEMA  [cinemaStart, cinemaEnd) with cinemaEnd = clamp(S-30, 03:00, 07:30)
    // EVENING [cinemaEnd, S+60) | STANDARD [S+60, D-30)

    @Test fun delhi_20_oct() {
        val s = hm(6, 24); val d = hm(17, 47)
        assertEquals(CINEMA, sched(hm(5, 53), s, d))
        assertEquals(EVENING, sched(hm(5, 54), s, d))
        assertEquals(EVENING, sched(hm(7, 23), s, d))
        assertEquals(STANDARD, sched(hm(7, 24), s, d))
        assertEquals(STANDARD, sched(hm(17, 16), s, d))
        assertEquals(EVENING, sched(hm(17, 17), s, d))
        // D+90 = 19:17 is before 20:30, so lamp-time evening lasts until 20:30.
        assertEquals(EVENING, sched(hm(20, 29), s, d))
        assertEquals(CINEMA, sched(hm(20, 30), s, d))
        assertEquals(CINEMA, sched(0, s, d))
    }

    @Test fun london_june() {
        // S 04:43, D 21:21: cinemaStart = D+90 = 22:51, cinemaEnd = S-30 = 04:13.
        val s = hm(4, 43); val d = hm(21, 21)
        assertEquals(CINEMA, sched(hm(4, 12), s, d))
        assertEquals(EVENING, sched(hm(4, 13), s, d))
        assertEquals(EVENING, sched(hm(5, 42), s, d))
        assertEquals(STANDARD, sched(hm(5, 43), s, d))
        assertEquals(STANDARD, sched(hm(20, 50), s, d))
        assertEquals(EVENING, sched(hm(20, 51), s, d))
        assertEquals(EVENING, sched(hm(22, 50), s, d))
        assertEquals(CINEMA, sched(hm(22, 51), s, d))
        assertEquals(CINEMA, sched(hm(1, 0), s, d))
    }

    @Test fun cinema_never_past_0730_and_never_ends_before_0300() {
        // Late sunrise 09:30: CINEMA ends at 07:30, then EVENING until S+60 = 10:30.
        val late = hm(9, 30); val d1 = hm(16, 0)
        assertEquals(CINEMA, sched(hm(7, 29), late, d1))
        assertEquals(EVENING, sched(hm(7, 30), late, d1))
        assertEquals(EVENING, sched(hm(10, 29), late, d1))
        assertEquals(STANDARD, sched(hm(10, 30), late, d1))
        assertEquals(EVENING, sched(hm(15, 30), late, d1))
        assertEquals(CINEMA, sched(hm(20, 30), late, d1))
        // Earliest plausible sunrise 03:00: S-30 = 02:30 is clamped to 03:00.
        val early = hm(3, 0); val d2 = hm(21, 30)
        assertEquals(CINEMA, sched(hm(2, 59), early, d2))
        assertEquals(EVENING, sched(hm(3, 0), early, d2))
        assertEquals(STANDARD, sched(hm(4, 0), early, d2))
        // Latest plausible sunset 21:30: CINEMA from 23:00.
        assertEquals(EVENING, sched(hm(22, 59), early, d2))
        assertEquals(CINEMA, sched(hm(23, 0), early, d2))
    }

    @Test fun cinema_never_starts_before_2030() {
        for ((s, d) in listOf(hm(6, 0) to hm(15, 0), hm(7, 0) to hm(17, 0), hm(4, 43) to hm(21, 21))) {
            for (m in hm(7, 30) until hm(20, 30)) assertTrue("$s/$d @$m", sched(m, s, d) != CINEMA)
            for (m in hm(7, 30) until hm(20, 30)) assertTrue(sched(m) != CINEMA)
        }
    }

    @Test fun never_bright_without_lux() {
        for (m in 0 until 1440) {
            assertTrue(sched(m, hm(6, 24), hm(17, 47)) != BRIGHT)
            assertTrue(sched(m) != BRIGHT)
        }
    }

    @Test fun implausible_or_polar_sun_times_use_the_no_sun_table() {
        val cases = listOf(
            null to hm(17, 47), hm(6, 24) to null,
            hm(20, 0) to hm(4, 0),            // polar-day style inversion
            hm(2, 59) to hm(17, 0),           // S before 03:00
            hm(9, 31) to hm(17, 0),           // S after 09:30
            hm(6, 0) to hm(14, 59),           // D before 15:00
            hm(6, 0) to hm(21, 31),           // D after 21:30
            -50 to 9999, 600 to 600
        )
        for ((s, d) in cases) for (m in 0 until 1440) assertEquals("$s/$d @$m", sched(m), sched(m, s, d))
    }

    // ---- Manual ----

    @Test fun manual_choice_wins_immediately() {
        var s = RoomLightModel.initial(inputs(null, 0))
        assertEquals(STANDARD, s.mode)
        s = RoomLightModel.next(s, inputs(null, 1000, RoomChoice.BRIGHT))
        assertEquals(BRIGHT, s.mode)
        assertEquals(1000L, s.since)
        assertNull(s.pending)
        // Even with lux saying dark.
        s = RoomLightModel.next(s, inputs(0f, 2000, RoomChoice.CINEMA))
        assertEquals(CINEMA, s.mode)
        s = RoomLightModel.next(s, inputs(0f, 3000, RoomChoice.CINEMA))
        assertEquals(2000L, s.since)
    }

    @Test fun manual_to_auto_applies_immediately_without_dwell() {
        var s = RoomLightModel.initial(inputs(null, 0, RoomChoice.BRIGHT))
        assertEquals(BRIGHT, s.mode)
        s = RoomLightModel.next(s, inputs(null, 1000, RoomChoice.AUTO, hm(21, 0)))
        assertEquals(EVENING, s.mode)
        // With lux too.
        s = RoomLightModel.next(s, inputs(0f, 2000, RoomChoice.STANDARD))
        s = RoomLightModel.next(s, inputs(1f, 3000, RoomChoice.AUTO))
        assertEquals(CINEMA, s.mode)
    }

    @Test fun auto_to_manual_and_back() {
        var s = RoomLightModel.initial(inputs(100f, 0))
        assertEquals(STANDARD, s.mode)
        s = RoomLightModel.next(s, inputs(100f, 1_000, RoomChoice.EVENING))
        assertEquals(EVENING, s.mode)
        s = RoomLightModel.next(s, inputs(100f, 2_000, RoomChoice.AUTO))
        assertEquals(STANDARD, s.mode)
    }

    // ---- Lux with hysteresis and dwell ----

    @Test fun lux_bands_from_cold_start() {
        assertEquals(CINEMA, RoomLightModel.initial(inputs(0f, 0)).mode)
        assertEquals(CINEMA, RoomLightModel.initial(inputs(4.9f, 0)).mode)
        assertEquals(EVENING, RoomLightModel.initial(inputs(5f, 0)).mode)
        assertEquals(EVENING, RoomLightModel.initial(inputs(39f, 0)).mode)
        assertEquals(STANDARD, RoomLightModel.initial(inputs(40f, 0)).mode)
        assertEquals(STANDARD, RoomLightModel.initial(inputs(399f, 0)).mode)
        assertEquals(BRIGHT, RoomLightModel.initial(inputs(400f, 0)).mode)
        assertEquals(BRIGHT, RoomLightModel.initial(inputs(80_000f, 0)).mode)
    }

    @Test fun hysteresis_thresholds() {
        // Rising needs +25 %.
        assertEquals(STANDARD, RoomLightModel.desiredForLux(450f, STANDARD))
        assertEquals(BRIGHT, RoomLightModel.desiredForLux(500f, STANDARD))
        // Falling needs -25 %.
        assertEquals(STANDARD, RoomLightModel.desiredForLux(31f, STANDARD))
        assertEquals(EVENING, RoomLightModel.desiredForLux(29.9f, STANDARD))
        assertEquals(BRIGHT, RoomLightModel.desiredForLux(301f, BRIGHT))
        assertEquals(STANDARD, RoomLightModel.desiredForLux(299f, BRIGHT))
        assertEquals(CINEMA, RoomLightModel.desiredForLux(6f, CINEMA))
        assertEquals(EVENING, RoomLightModel.desiredForLux(6.25f, CINEMA))
        // Large drops go straight to the right band.
        assertEquals(CINEMA, RoomLightModel.desiredForLux(1f, BRIGHT))
        assertEquals(EVENING, RoomLightModel.desiredForLux(10f, BRIGHT))
    }

    @Test fun dwell_45s_for_one_step() {
        var s = RoomLightModel.initial(inputs(100f, 0))
        s = RoomLightModel.next(s, inputs(10f, 1_000))
        assertEquals(STANDARD, s.mode)
        assertEquals(EVENING, s.pending)
        s = RoomLightModel.next(s, inputs(10f, 45_999))
        assertEquals(STANDARD, s.mode)
        s = RoomLightModel.next(s, inputs(10f, 46_000))
        assertEquals(EVENING, s.mode)
        assertEquals(46_000L, s.since)
        assertNull(s.pending)
    }

    @Test fun dwell_10s_for_big_jump_brighter() {
        var s = RoomLightModel.initial(inputs(1f, 0))
        assertEquals(CINEMA, s.mode)
        s = RoomLightModel.next(s, inputs(5_000f, 1_000))
        assertEquals(BRIGHT, s.pending)
        s = RoomLightModel.next(s, inputs(5_000f, 10_999))
        assertEquals(CINEMA, s.mode)
        s = RoomLightModel.next(s, inputs(5_000f, 11_000))
        assertEquals(BRIGHT, s.mode)
    }

    @Test fun big_jump_darker_still_waits_45s() {
        var s = RoomLightModel.initial(inputs(5_000f, 0))
        s = RoomLightModel.next(s, inputs(0.5f, 1_000))
        s = RoomLightModel.next(s, inputs(0.5f, 20_000))
        assertEquals(BRIGHT, s.mode)
        s = RoomLightModel.next(s, inputs(0.5f, 46_000))
        assertEquals(CINEMA, s.mode)
    }

    @Test fun pending_cancelled_when_lux_returns() {
        var s = RoomLightModel.initial(inputs(100f, 0))
        s = RoomLightModel.next(s, inputs(10f, 1_000))
        s = RoomLightModel.next(s, inputs(100f, 20_000))
        assertNull(s.pending)
        s = RoomLightModel.next(s, inputs(10f, 30_000))
        s = RoomLightModel.next(s, inputs(10f, 60_000))
        assertEquals(STANDARD, s.mode)
        s = RoomLightModel.next(s, inputs(10f, 75_000))
        assertEquals(EVENING, s.mode)
    }

    @Test fun pending_same_direction_keeps_its_clock() {
        // Brightening in two steps (EVENING then STANDARD) counts from the first sample.
        var s = RoomLightModel.initial(inputs(1f, 0))
        s = RoomLightModel.next(s, inputs(10f, 1_000))
        assertEquals(EVENING, s.pending)
        s = RoomLightModel.next(s, inputs(60f, 5_000))
        assertEquals(CINEMA, s.mode)
        assertEquals(STANDARD, s.pending)
        assertEquals(1_000L, s.pendingSince)
        s = RoomLightModel.next(s, inputs(60f, 10_999))
        assertEquals(CINEMA, s.mode)
        // Two steps brighter -> 10 s dwell counted from 1 000.
        s = RoomLightModel.next(s, inputs(60f, 11_000))
        assertEquals(STANDARD, s.mode)
    }

    @Test fun noisy_lux_around_threshold_does_not_flap() {
        val rnd = java.util.Random(42)
        val smoother = LuxSmoother(alpha = 0.2f, clock = { t })
        var s = RoomLightModel.initial(inputs(40f, 0))
        var switches = 0
        var now = 0L
        repeat(2_000) {
            now += 2_000; t = now
            // 40 lux +/- 30 % noise and an occasional spike.
            var lux = 40f * (1f + (rnd.nextFloat() - 0.5f) * 0.6f)
            if (rnd.nextInt(50) == 0) lux = 2_000f
            val smooth = smoother.add(lux)
            val n = RoomLightModel.next(s, inputs(smooth, now))
            if (n.mode != s.mode) switches++
            s = n
        }
        assertTrue("switches=$switches", switches <= 1)
    }

    @Test fun raw_jitter_without_smoothing_still_bounded_by_dwell() {
        var s = RoomLightModel.initial(inputs(40f, 0))
        var switches = 0
        var now = 0L
        // Alternating 25 / 55 lux every 2 s, for an hour.
        repeat(1_800) { i ->
            now += 2_000
            val n = RoomLightModel.next(s, inputs(if (i % 2 == 0) 25f else 55f, now))
            if (n.mode != s.mode) switches++
            s = n
        }
        assertTrue("switches=$switches", switches <= 2)
    }

    @Test fun lux_lost_falls_back_to_schedule_immediately() {
        var s = RoomLightModel.initial(inputs(5_000f, 0))
        assertEquals(BRIGHT, s.mode)
        s = RoomLightModel.next(s, inputs(null, 1_000, min = hm(12, 0)))
        assertEquals(STANDARD, s.mode)
    }

    @Test fun schedule_changes_apply_at_the_boundary() {
        var s = RoomLightModel.initial(inputs(null, 0, min = hm(18, 29)))
        assertEquals(STANDARD, s.mode)
        s = RoomLightModel.next(s, inputs(null, 60_000, min = hm(18, 30)))
        assertEquals(EVENING, s.mode)
        assertEquals(60_000L, s.since)
    }

    @Test fun invalid_lux_treated_as_absent() {
        assertEquals(STANDARD, RoomLightModel.initial(inputs(Float.NaN, 0)).mode)
        assertEquals(STANDARD, RoomLightModel.initial(inputs(-3f, 0)).mode)
        assertEquals(STANDARD, RoomLightModel.initial(inputs(Float.POSITIVE_INFINITY, 0)).mode)
    }

    @Test fun clock_going_backwards_does_not_stick() {
        var s = RoomLightModel.initial(inputs(100f, 100_000))
        s = RoomLightModel.next(s, inputs(10f, 101_000))
        s = RoomLightModel.next(s, inputs(10f, 5_000))
        // Pending restarted from the new clock; later it still switches.
        s = RoomLightModel.next(s, inputs(10f, 51_000))
        assertEquals(EVENING, s.mode)
    }

    @Test fun every_minute_of_a_day_with_sun_inputs_never_crashes() {
        var s = RoomLightModel.initial(inputs(null, 0))
        for (m in 0 until 1440) {
            s = RoomLightModel.next(s, RoomLightModel.Inputs(null, m, hm(20, 0), hm(4, 0), RoomChoice.AUTO, m * 60_000L))
            s = RoomLightModel.next(s, RoomLightModel.Inputs(null, m, 0, 1439, RoomChoice.AUTO, m * 60_000L))
        }
    }

    private var t = 0L
}
