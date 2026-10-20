package dev.tvlauncher.splash

/**
 * Pure sequencing of the start-up styles other than the sunrise (which is [dev.tvlauncher.ui.SplashReveal]).
 * Offsets are from the start of the overlay. Every timeline is scaled by the [MotionPace] and then fitted
 * under [MotionCaps.startup] for the level, so LITE never passes 700 ms and FULL never 1100 ms.
 */
object StartupTimelines {
    enum class Part {
        /** Aurora: the colour band sweeping across. */
        SWEEP,
        /** The M02 mark resolving. */
        MARK,
        /** The MINTAKA caption (FULL only). */
        CAPTION,
        /** The overlay leaving. */
        OUT,
        /** The home content rising in. */
        CONTENT
    }

    data class Step(val part: Part, val startMs: Long, val durationMs: Long)

    data class Timeline(val steps: List<Step>) {
        val totalMs: Long get() = steps.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        /** When the intro hands over to the home (start of OUT). */
        val introEndMs: Long get() = first(Part.OUT)?.startMs ?: totalMs
        fun first(part: Part): Step? = steps.firstOrNull { it.part == part }
        /** Starts never go backwards and every step has a positive length. */
        val isMonotonic: Boolean get() = steps.zipWithNext().all { (a, b) -> b.startMs >= a.startMs } &&
            steps.all { it.durationMs > 0 && it.startMs >= 0 }
    }

    fun minimal(full: Boolean, pace: MotionPace): Timeline {
        val base = if (full) listOf(
            Step(Part.MARK, 0, 380),
            Step(Part.CAPTION, 180, 260),
            Step(Part.OUT, 560, 240),
            Step(Part.CONTENT, 620, 320)
        ) else listOf(
            Step(Part.MARK, 0, 240),
            Step(Part.OUT, 320, 160),
            Step(Part.CONTENT, 360, 260)
        )
        return fit(base, pace, full)
    }

    fun aurora(full: Boolean, pace: MotionPace): Timeline {
        val base = if (full) listOf(
            Step(Part.SWEEP, 0, 620),
            Step(Part.MARK, 200, 320),
            Step(Part.CAPTION, 320, 220),
            Step(Part.OUT, 620, 220),
            Step(Part.CONTENT, 660, 300)
        ) else listOf(
            Step(Part.SWEEP, 0, 380),
            Step(Part.MARK, 120, 220),
            Step(Part.OUT, 380, 160),
            Step(Part.CONTENT, 420, 260)
        )
        return fit(base, pace, full)
    }

    /** The timeline of [style]; null for SUNRISE (own model) and OFF (nothing plays). */
    fun of(style: StartupStyle, full: Boolean, pace: MotionPace): Timeline? = when (style) {
        StartupStyle.MINIMAL -> minimal(full, pace)
        StartupStyle.AURORA -> aurora(full, pace)
        StartupStyle.SUNRISE, StartupStyle.OFF -> null
    }

    /** Scales [steps] by the pace, then shrinks everything proportionally if it would pass the cap. */
    internal fun fit(steps: List<Step>, pace: MotionPace, full: Boolean): Timeline {
        val cap = MotionCaps.startup(full)
        val rawTotal = steps.maxOf { it.startMs + it.durationMs } * pace.factor
        val f = if (rawTotal > cap) pace.factor * cap / rawTotal else pace.factor
        return Timeline(steps.map { s ->
            val start = (s.startMs * f).toLong()
            val end = ((s.startMs + s.durationMs) * f).toLong().coerceAtMost(cap)
            Step(s.part, start, (end - start).coerceAtLeast(1L))
        })
    }
}
