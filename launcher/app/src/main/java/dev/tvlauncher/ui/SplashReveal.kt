package dev.tvlauncher.ui

/**
 * Pure sequencing of the cold-start reveal. Offsets are relative to the start of the reveal.
 * FULL: logo mark in/out, then top bar, hero, a left-to-right tile stagger and the dock.
 * LITE: a short mark and one fade+translate of the whole content.
 */
object SplashReveal {
    const val MAX_STAGGERED_TILES = 6
    const val TILE_STAGGER_MS = 40L

    enum class Part { MARK_IN, MARK_OUT, TOP_BAR, HERO, TILE, DOCK, CONTENT }

    data class Step(val part: Part, val startMs: Long, val durationMs: Long, val index: Int = 0)

    data class Timeline(val steps: List<Step>) {
        val totalMs: Long get() = steps.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        fun first(part: Part): Step? = steps.firstOrNull { it.part == part }
        fun tile(index: Int): Step? = steps.firstOrNull { it.part == Part.TILE && it.index == index }
    }

    fun full(visibleTiles: Int): Timeline {
        val n = visibleTiles.coerceIn(0, MAX_STAGGERED_TILES)
        val steps = ArrayList<Step>()
        steps += Step(Part.MARK_IN, 0, 320)
        steps += Step(Part.MARK_OUT, 300, 220)
        steps += Step(Part.TOP_BAR, 400, 320)
        steps += Step(Part.HERO, 440, 340)
        for (i in 0 until n) steps += Step(Part.TILE, 480 + i * TILE_STAGGER_MS, 320, i)
        steps += Step(Part.DOCK, 480 + n * TILE_STAGGER_MS, 320)
        return Timeline(steps)
    }

    fun lite(): Timeline = Timeline(
        listOf(
            Step(Part.MARK_IN, 0, 200),
            Step(Part.MARK_OUT, 180, 180),
            Step(Part.CONTENT, 260, 320)
        )
    )
}
