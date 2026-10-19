package dev.tvlauncher.room

/**
 * Fixed day schedule used by AUTO when there is no light sensor and no sunrise/sunset.
 * Minutes of the LOCAL day (0..1439); the cycle is dayStart -> eveningStart -> nightStart -> dayStart
 * and may wrap midnight (e.g. nightStart = 01:00).
 */
data class Schedule(
    val dayStart: Int = 7 * 60,
    val eveningStart: Int = 18 * 60 + 30,
    val nightStart: Int = 23 * 60 + 30
) {
    companion object {
        val DEFAULT = Schedule()
    }
}

/**
 * Pure state machine deciding the [RoomMode]. No Android, no clocks: everything comes in [Inputs].
 *
 * Priority:
 * 1. A manual [RoomChoice] wins immediately.
 * 2. AUTO with a (smoothed, see [LuxSmoother]) lux reading: bands with +/-25 % hysteresis and a
 *    dwell before switching ([DWELL_MS]; [FAST_DWELL_MS] when brightening by 2+ steps, so a sunny
 *    reveal isn't held back).
 * 3. AUTO without lux: the time-of-day schedule ([scheduleMode]); it switches at the boundary,
 *    no dwell (it can't flap). BRIGHT is never chosen from the schedule: without a sensor we can't
 *    know whether the sun is on the screen, and a wrong BRIGHT looks washed out at night.
 *
 * Entering AUTO (first run, or coming back from a manual choice) applies the AUTO result at once,
 * using plain band edges without hysteresis.
 */
object RoomLightModel {
    /** Rising band edges in lux: <5 CINEMA, 5..40 EVENING, 40..400 STANDARD, >=400 BRIGHT. */
    val LUX_EDGES = floatArrayOf(5f, 40f, 400f)
    /** To move up past an edge the lux must reach edge * ENTER; to fall below it, edge * EXIT. */
    const val ENTER = 1.25f
    const val EXIT = 0.75f
    const val DWELL_MS = 45_000L
    const val FAST_DWELL_MS = 10_000L
    /** Minutes either side of sunrise/sunset that count as EVENING (total 90). */
    const val SUN_BEFORE_SET = 60
    const val SUN_AFTER_SET = 30
    const val SUN_BEFORE_RISE = 30
    const val SUN_AFTER_RISE = 60
    /** Shortest believable day for the sun schedule (else the fixed schedule is used). */
    const val MIN_DAY_MIN = 120

    data class Inputs(
        val lux: Float?,
        val minuteOfDay: Int,
        val sunriseMin: Int?,
        val sunsetMin: Int?,
        val choice: RoomChoice,
        val nowMs: Long,
        val schedule: Schedule = Schedule.DEFAULT
    )

    /**
     * [pending]/[pendingSince]: an AUTO-lux change waiting for its dwell. [reenter]: the next AUTO
     * evaluation applies immediately (set on first run and while a manual choice is active).
     */
    data class State(
        val mode: RoomMode,
        val since: Long,
        val pending: RoomMode?,
        val pendingSince: Long,
        val reenter: Boolean = false
    )

    /** The state for a cold start: whatever the inputs say, applied immediately. */
    fun initial(inputs: Inputs): State =
        next(State(RoomMode.STANDARD, inputs.nowMs, null, inputs.nowMs, reenter = true), inputs)

    fun next(prev: State, inputs: Inputs): State {
        val now = inputs.nowMs
        val manual = inputs.choice.manualMode
        if (manual != null) {
            return if (prev.mode == manual && prev.pending == null && prev.reenter) prev
            else State(manual, if (prev.mode == manual) prev.since else now, null, now, reenter = true)
        }
        val lux = inputs.lux?.takeIf { it.isFinite() && it >= 0f }
        if (lux == null) {
            val m = scheduleMode(inputs.minuteOfDay, inputs.sunriseMin, inputs.sunsetMin, inputs.schedule)
            return settle(prev, m, now)
        }
        if (prev.reenter) return settle(prev, bandForLux(lux), now)

        val desired = desiredForLux(lux, prev.mode)
        if (desired == prev.mode) {
            return if (prev.pending == null) prev else prev.copy(pending = null, pendingSince = now)
        }
        val sameDirection = prev.pending != null &&
            (prev.pending.ordinal > prev.mode.ordinal) == (desired.ordinal > prev.mode.ordinal)
        // A clock that went backwards restarts the wait instead of freezing it.
        val since = if (sameDirection && prev.pendingSince <= now) prev.pendingSince else now
        val wait = if (desired.ordinal - prev.mode.ordinal >= 2) FAST_DWELL_MS else DWELL_MS
        return if (now - since >= wait) State(desired, now, null, now)
        else State(prev.mode, prev.since, desired, since)
    }

    private fun settle(prev: State, m: RoomMode, now: Long): State =
        if (prev.mode == m && prev.pending == null && !prev.reenter) prev
        else State(m, if (prev.mode == m) prev.since else now, null, now)

    /** Band from plain edges (no hysteresis). */
    fun bandForLux(lux: Float): RoomMode = RoomMode.values()[LUX_EDGES.count { lux >= it }]

    /** Band with hysteresis around the [current] mode. */
    fun desiredForLux(lux: Float, current: RoomMode): RoomMode {
        val up = LUX_EDGES.count { lux >= it * ENTER }
        if (up > current.ordinal) return RoomMode.values()[up]
        val down = LUX_EDGES.count { lux >= it * EXIT }
        if (down < current.ordinal) return RoomMode.values()[down]
        return current
    }

    /**
     * Time-of-day mode (never BRIGHT). With believable sun times: EVENING in
     * [sunrise-30, sunrise+60) and [sunset-60, sunset+30), STANDARD between, CINEMA otherwise, plus
     * a hard night [schedule.nightStart, schedule.dayStart-30) (23:30..06:30 by default) that is
     * always CINEMA. Without them (missing, sunrise >= sunset, out of range, polar): [schedule].
     */
    fun scheduleMode(minuteOfDay: Int, sunriseMin: Int?, sunsetMin: Int?, schedule: Schedule = Schedule.DEFAULT): RoomMode {
        val m = mod(minuteOfDay)
        val sched = sane(schedule)
        if (sunriseMin == null || sunsetMin == null || sunriseMin !in 0 until DAY || sunsetMin !in 0 until DAY ||
            sunsetMin - sunriseMin < MIN_DAY_MIN
        ) return fixed(m, sched)
        if (inWindow(m, sched.nightStart, sched.dayStart - 30)) return RoomMode.CINEMA
        if (inWindow(m, sunriseMin - SUN_BEFORE_RISE, sunriseMin + SUN_AFTER_RISE)) return RoomMode.EVENING
        if (inWindow(m, sunsetMin - SUN_BEFORE_SET, sunsetMin + SUN_AFTER_SET)) return RoomMode.EVENING
        if (inWindow(m, sunriseMin + SUN_AFTER_RISE, sunsetMin - SUN_BEFORE_SET)) return RoomMode.STANDARD
        return RoomMode.CINEMA
    }

    private const val DAY = 1440

    private fun mod(m: Int) = ((m % DAY) + DAY) % DAY

    /** [start, end) on the 24 h circle; empty when start == end. */
    private fun inWindow(m: Int, start: Int, end: Int): Boolean {
        val len = mod(end - start)
        return mod(m - start) < len
    }

    private fun sane(s: Schedule): Schedule {
        val d = mod(s.dayStart)
        val e = mod(s.eveningStart - d)
        val n = mod(s.nightStart - d)
        return if (e <= n) Schedule(d, mod(s.eveningStart), mod(s.nightStart)) else Schedule.DEFAULT
    }

    private fun fixed(m: Int, s: Schedule): RoomMode {
        val x = mod(m - s.dayStart)
        return when {
            x < mod(s.eveningStart - s.dayStart) -> RoomMode.STANDARD
            x < mod(s.nightStart - s.dayStart) -> RoomMode.EVENING
            else -> RoomMode.CINEMA
        }
    }
}
