package dev.tvlauncher.ui

/**
 * Pure sequencing of the cold-start reveal (the "sunrise"). Offsets are relative to the start of the reveal.
 * Intro (runs while apps load): a horizon line draws across, a small sun-disc rises on it, the "M02" mark
 * resolves above it and the letter-spaced "MINTAKA" caption fades in below. Then the mark leaves and the
 * home rises: FULL = top bar, hero, a left-to-right tile stagger and the dock; LITE = one fade+rise of the
 * whole content. FULL ends by [FULL_BUDGET_MS], LITE by [LITE_BUDGET_MS].
 */
object SplashReveal {
    const val MAX_STAGGERED_TILES = 6
    const val TILE_STAGGER_MS = 32L
    const val FULL_BUDGET_MS = 1100L
    const val LITE_BUDGET_MS = 700L

    enum class Part { LINE, SUN, MARK, CAPTION, MARK_OUT, TOP_BAR, HERO, TILE, DOCK, CONTENT }

    /** Parts played by phase 1 (SplashOverlay.start), before the app list is known. */
    val INTRO: Set<Part> = setOf(Part.LINE, Part.SUN, Part.MARK, Part.CAPTION)

    data class Step(val part: Part, val startMs: Long, val durationMs: Long, val index: Int = 0)

    data class Timeline(val steps: List<Step>) {
        val totalMs: Long get() = steps.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        /** When the intro hands over to the home (start of MARK_OUT). */
        val introEndMs: Long get() = first(Part.MARK_OUT)?.startMs ?: totalMs
        fun first(part: Part): Step? = steps.firstOrNull { it.part == part }
        fun tile(index: Int): Step? = steps.firstOrNull { it.part == Part.TILE && it.index == index }
    }

    fun full(visibleTiles: Int): Timeline {
        val n = visibleTiles.coerceIn(0, MAX_STAGGERED_TILES)
        val steps = ArrayList<Step>()
        steps += Step(Part.LINE, 0, 260)
        steps += Step(Part.SUN, 140, 300)
        steps += Step(Part.MARK, 260, 300)
        steps += Step(Part.CAPTION, 360, 200)
        steps += Step(Part.MARK_OUT, 560, 220)
        steps += Step(Part.TOP_BAR, 600, 280)
        steps += Step(Part.HERO, 630, 300)
        for (i in 0 until n) steps += Step(Part.TILE, 660 + i * TILE_STAGGER_MS, 260, i)
        steps += Step(Part.DOCK, 660 + n * TILE_STAGGER_MS, 240)
        return Timeline(steps)
    }

    /** Upper bound of the whole reveal (no splash input, e.g. focus sounds, before this). */
    fun totalMs(full: Boolean): Long = if (full) full(MAX_STAGGERED_TILES).totalMs else lite().totalMs

    fun lite(): Timeline = Timeline(
        listOf(
            Step(Part.LINE, 0, 160),
            Step(Part.SUN, 80, 180),
            Step(Part.MARK, 140, 180),
            Step(Part.CAPTION, 200, 140),
            Step(Part.MARK_OUT, 340, 160),
            Step(Part.CONTENT, 400, 280)
        )
    )
}
