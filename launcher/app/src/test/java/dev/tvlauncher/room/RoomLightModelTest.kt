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

    // ---- Fixed schedule (no sun times) ----

    @Test fun default_schedule_boundaries() {
        assertEquals(CINEMA, sched(hm(6, 59)))
        assertEquals(STANDARD, sched(hm(7, 0)))
        assertEquals(STANDARD, sched(hm(18, 29)))
        assertEquals(EVENING, sched(hm(18, 30)))
        assertEquals(EVENING, sched(hm(23, 29)))
        assertEquals(CINEMA, sched(hm(23, 30)))
        assertEquals(CINEMA, sched(0))
        assertEquals(CINEMA, sched(1439))
        assertEquals(STANDARD, sched(hm(12, 0)))
    }

    @Test fun out_of_range_minutes_wrap() {
        assertEquals(sched(hm(12, 0)), sched(hm(12, 0) + 1440))
        assertEquals(sched(hm(23, 50)), sched(-10))
    }

    @Test fun custom_schedule_respected() {
        val s = Schedule(dayStart = hm(8, 0), eveningStart = hm(20, 0), nightStart = hm(1, 0))
        assertEquals(CINEMA, sched(hm(7, 59), schedule = s))
        assertEquals(STANDARD, sched(hm(8, 0), schedule = s))
        assertEquals(EVENING, sched(hm(20, 0), schedule = s))
        // Night start after midnight: 23:59 and 00:30 are still evening.
        assertEquals(EVENING, sched(hm(23, 59), schedule = s))
        assertEquals(EVENING, sched(hm(0, 30), schedule = s))
        assertEquals(CINEMA, sched(hm(1, 0), schedule = s))
    }

    @Test fun degenerate_schedule_does_not_crash() {
        val s = Schedule(dayStart = 600, eveningStart = 600, nightStart = 600)
        for (m in 0 until 1440) sched(m, schedule = s)
    }

    // ---- Sun schedule ----

    private val rise = hm(6, 25)
    private val set = hm(17, 48)

    @Test fun sun_schedule_boundaries() {
        // Night until sunrise-30.
        assertEquals(CINEMA, sched(hm(5, 54), rise, set))
        // Hard night: before 06:30 stays CINEMA even though it is inside sunrise-30.
        assertEquals(CINEMA, sched(hm(5, 55), rise, set))
        assertEquals(CINEMA, sched(hm(6, 29), rise, set))
        // Sunrise window [sunrise-30, sunrise+60) once past the hard-night end.
        assertEquals(EVENING, sched(hm(6, 30), rise, set))
        assertEquals(EVENING, sched(rise + 59, rise, set))
        assertEquals(STANDARD, sched(rise + 60, rise, set))
        assertEquals(STANDARD, sched(set - 61, rise, set))
        assertEquals(EVENING, sched(set - 60, rise, set))
        assertEquals(EVENING, sched(set + 29, rise, set))
        assertEquals(CINEMA, sched(set + 30, rise, set))
        assertEquals(CINEMA, sched(hm(23, 0), rise, set))
        assertEquals(CINEMA, sched(0, rise, set))
    }

    @Test fun late_sunrise_morning_window() {
        // Winter London: sunrise 08:04, sunset 15:53.
        val r = hm(8, 4); val s = hm(15, 53)
        assertEquals(CINEMA, sched(hm(7, 33), r, s))
        assertEquals(EVENING, sched(hm(7, 34), r, s))
        assertEquals(STANDARD, sched(hm(9, 4), r, s))
        assertEquals(EVENING, sched(hm(14, 53), r, s))
        assertEquals(CINEMA, sched(hm(16, 23), r, s))
    }

    @Test fun summer_late_sunset_still_hard_night_after_2330() {
        // Far-north summer: sunset 23:10 local.
        val r = hm(3, 30); val s = hm(23, 10)
        assertEquals(EVENING, sched(hm(23, 0), r, s))
        assertEquals(CINEMA, sched(hm(23, 30), r, s))
        assertEquals(CINEMA, sched(hm(3, 0), r, s))
        // 06:30 onwards: the sunrise window is long past: day.
        assertEquals(STANDARD, sched(hm(6, 30), r, s))
    }

    @Test fun sun_schedule_never_bright() {
        for (m in 0 until 1440) {
            assertTrue(sched(m, rise, set) != BRIGHT)
            assertTrue(sched(m) != BRIGHT)
        }
    }

    @Test fun missing_or_anomalous_sun_times_fall_back_to_fixed_schedule() {
        for (m in 0 until 1440 step 7) {
            val fixed = sched(m)
            assertEquals(fixed, sched(m, null, set))
            assertEquals(fixed, sched(m, rise, null))
            // Polar-day style anomaly: sunrise after sunset.
            assertEquals(fixed, sched(m, hm(20, 0), hm(4, 0)))
            // Garbage values.
            assertEquals(fixed, sched(m, -50, 9999))
            assertEquals(fixed, sched(m, 600, 600))
        }
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
        val st = RoomLightModel.State(STANDARD, 0, null, 0)
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
        assertEquals(STANDARD, st.mode)
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
