package dev.tvlauncher.homesections

/**
 * The stacked home drawn as blocks for the Home sections preview (fractions of the preview, 0..1).
 * Pure. Top bar, a hero strip for the focus row, then each enabled section as a band: app rows as
 * tiles (the first tile of the focus row lit), other sections as one wide card. Sections past the
 * first page are drawn faded below the fold line.
 */
object SectionPreview {
    enum class Part { TOP_BAR, HERO, TITLE, TILE, TILE_FOCUSED, CARD, FOLD }
    data class Block(val part: Part, val left: Float, val top: Float, val right: Float, val bottom: Float, val faded: Boolean = false)

    private const val SIDE = 0.05f
    private const val TOP = 0.05f
    private const val BAR_H = 0.06f
    private const val HERO_H = 0.12f
    private const val FOLD = 0.95f

    fun blocks(s: HomeSections): List<Block> {
        val out = ArrayList<Block>()
        out += Block(Part.TOP_BAR, SIDE, TOP, 1f - SIDE, TOP + BAR_H)
        out += Block(Part.HERO, SIDE, TOP + BAR_H + 0.03f, 0.45f, TOP + BAR_H + 0.03f + HERO_H * 0.5f)
        var y = TOP + BAR_H + HERO_H + 0.04f
        val list = s.enabled.filter { it.type.drawn }
        list.forEachIndexed { i, sec ->
            val faded = y >= FOLD
            val titleH = if (sec.showTitle) 0.03f else 0f
            if (titleH > 0f) out += Block(Part.TITLE, SIDE, y, SIDE + 0.18f, y + titleH * 0.7f, faded)
            y += titleH
            val lines = SectionLayout.lines(sec)
            val tileH = when (sec.style) {
                SectionStyle.COMPACT -> 0.06f
                SectionStyle.BIG_CARDS -> 0.16f
                else -> 0.11f
            } * sec.size.scale
            if (sec.type.appRow) {
                val tileW = tileH // a 16:9 tile in a 16:9 preview: the same fraction both ways
                var x = SIDE
                var idx = 0
                for (line in 0 until lines) {
                    x = SIDE
                    while (x + tileW <= 1f - SIDE + 0.001f) {
                        val part = if (i == 0 && idx == 0) Part.TILE_FOCUSED else Part.TILE
                        out += Block(part, x, y + line * (tileH + 0.01f), x + tileW, y + line * (tileH + 0.01f) + tileH, faded)
                        x += tileW + 0.015f
                        idx++
                    }
                }
                y += lines * (tileH + 0.01f) + 0.02f
            } else {
                out += Block(Part.CARD, SIDE, y, 0.6f, y + 0.08f, faded)
                y += 0.10f
            }
        }
        if (y > FOLD) out += Block(Part.FOLD, SIDE, FOLD, 1f - SIDE, FOLD + 0.004f)
        return out.filter { it.top < 1f }.map { it.copy(bottom = minOf(it.bottom, 1f)) }
    }
}
