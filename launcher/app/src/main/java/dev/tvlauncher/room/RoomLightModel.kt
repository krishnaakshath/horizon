package dev.tvlauncher.room

/**
 * The no-sun schedule (user-overridable; minutes of the LOCAL day, 0..1439). The cycle is
 * morning EVENING [dayStart-60, dayStart) -> STANDARD [dayStart, eveningStart) -> EVENING
 * [eveningStart, nightStart) -> CINEMA [nightStart, dayStart-60), wrapping midnight as needed.
 * Invalid combinations are replaced by [DEFAULT] (see [RoomLightModel.sane]).
 */
data class Schedule(
    val dayStart: Int = 7 * 60 + 30,
    val eveningStart: Int = 18 * 60 + 30,
    val nightStart: Int = 23 * 60 + 30
) {
    /** End of the night = start of the morning lamp-time EVENING hour. */
    val nightEnd: Int get() = Math.floorMod(dayStart - MORNING_MIN, 1440)

    companion object {
        const val MORNING_MIN = 60
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
 * 3. AUTO without lux: the time-of-day tables ([scheduleMode]); it switches at the boundary,
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
    /** Sun table constants (minutes of the local day / offsets). See [scheduleMode]. */
    const val SUNSET_EVENING_BEFORE = 30
    const val SUNSET_CINEMA_AFTER = 90
    const val CINEMA_EARLIEST_START = 20 * 60 + 30
    const val CINEMA_LATEST_END = 7 * 60 + 30
    const val CINEMA_EARLIEST_END = 3 * 60
    const val SUNRISE_EVENING_BEFORE = 30
    const val SUNRISE_EVENING_AFTER = 60
    /** Plausible local sunrise / sunset; outside these the sun times are ignored. */
    val SUNRISE_RANGE = 3 * 60..9 * 60 + 30
    val SUNSET_RANGE = 15 * 60..21 * 60 + 30
    /** Accepted night length for user schedules, minutes. */
    val NIGHT_LENGTH_RANGE = 3 * 60..12 * 60

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
     * Time-of-day mode. Never BRIGHT: a clock can't know about sun glare.
     *
     * No sun times (missing or implausible: S outside 03:00-09:30, D outside 15:00-21:30, S >= D):
     *
     * | from  | to    | mode     |
     * |-------|-------|----------|
     * | 23:30 | 06:30 | CINEMA   |
     * | 06:30 | 07:30 | EVENING  |
     * | 07:30 | 18:30 | STANDARD |
     * | 18:30 | 23:30 | EVENING  |
     *
     * (defaults; [schedule] overrides dayStart / eveningStart / nightStart, night ends 60 min
     * before dayStart.)
     *
     * With sunrise S and sunset D (local minutes):
     *
     * | from                                   | to                     | mode     |
     * |----------------------------------------|------------------------|----------|
     * | cinemaEnd = clamp(S-30, 03:00, 07:30)  | S+60                   | EVENING  |
     * | S+60                                   | D-30                   | STANDARD |
     * | D-30                                   | cinemaStart            | EVENING  |
     * | cinemaStart = max(D+90, 20:30)         | cinemaEnd (next day)   | CINEMA   |
     *
     * So CINEMA never starts before 20:30 and never lasts past 07:30 in either table's spirit,
     * and lamp-time evenings stay EVENING.
     */
    fun scheduleMode(minuteOfDay: Int, sunriseMin: Int?, sunsetMin: Int?, schedule: Schedule = Schedule.DEFAULT): RoomMode {
        val m = mod(minuteOfDay)
        if (sunriseMin == null || sunsetMin == null || sunriseMin !in SUNRISE_RANGE || sunsetMin !in SUNSET_RANGE ||
            sunriseMin >= sunsetMin
        ) return fixed(m, sane(schedule))
        val s = sunriseMin; val d = sunsetMin
        val cinemaStart = maxOf(d + SUNSET_CINEMA_AFTER, CINEMA_EARLIEST_START)
        val cinemaEnd = (s - SUNRISE_EVENING_BEFORE).coerceIn(CINEMA_EARLIEST_END, CINEMA_LATEST_END)
        return when {
            inWindow(m, cinemaStart, cinemaEnd) -> RoomMode.CINEMA
            inWindow(m, cinemaEnd, s + SUNRISE_EVENING_AFTER) -> RoomMode.EVENING
            inWindow(m, s + SUNRISE_EVENING_AFTER, d - SUNSET_EVENING_BEFORE) -> RoomMode.STANDARD
            else -> RoomMode.EVENING
        }
    }

    private const val DAY = 1440

    private fun mod(m: Int) = ((m % DAY) + DAY) % DAY

    /** [start, end) on the 24 h circle; empty when start == end. */
    private fun inWindow(m: Int, start: Int, end: Int): Boolean {
        val len = mod(end - start)
        return mod(m - start) < len
    }

    /**
     * [s] if usable, else [Schedule.DEFAULT]: every value in 0..1439, the cycle order
     * day -> evening -> night -> morning holds with non-empty day and evening, and the night
     * lasts 3..12 h (so no window can cover ~the whole day).
     */
    fun sane(s: Schedule): Schedule {
        if (s.dayStart !in 0 until DAY || s.eveningStart !in 0 until DAY || s.nightStart !in 0 until DAY) return Schedule.DEFAULT
        val e = mod(s.eveningStart - s.dayStart)
        val n = mod(s.nightStart - s.dayStart)
        val morning = DAY - Schedule.MORNING_MIN
        if (e <= 0 || n <= e || n >= morning) return Schedule.DEFAULT
        if ((morning - n) !in NIGHT_LENGTH_RANGE) return Schedule.DEFAULT
        return s
    }

    private fun fixed(m: Int, s: Schedule): RoomMode {
        val x = mod(m - s.dayStart)
        return when {
            x < mod(s.eveningStart - s.dayStart) -> RoomMode.STANDARD
            x < mod(s.nightStart - s.dayStart) -> RoomMode.EVENING
            x < DAY - Schedule.MORNING_MIN -> RoomMode.CINEMA
            else -> RoomMode.EVENING
        }
    }
}
