package dev.tvlauncher.homelayout

import dev.tvlauncher.ui.TileSpec

/**
 * The small schematic of a [LayoutPlan] shown beside the Layout style rows: blocks in fractions of
 * the screen (0..1), computed once per choice (the view only scales and draws them). Uses the
 * same dp numbers as the home screen, so it is also how tests check that a style fits.
 */
object PreviewSchematic {
    enum class Part { TOP_CLOCK, TOP_PILL, HERO_TITLE, HERO_SUBTITLE, HERO_ART, TILE, TILE_FOCUSED, STRIP }

    /** One block; right/bottom exclusive, in fractions of the screen. */
    data class Block(val part: Part, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun intersects(o: Block) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    }

    private const val CLOCK_W_DP = 64
    private const val PILL_W_DP = 40
    private const val PILL_GAP_DP = 12
    private const val PILLS = 3
    private const val TITLE_H_DP = 30
    private const val SUBTITLE_H_DP = 12
    private const val STRIP_W_DP = 120
    private const val STRIP_H_DP = 40

    fun blocks(plan: LayoutPlan, screen: ScreenDp): List<Block> {
        val w = screen.widthDp.toFloat()
        val h = screen.heightDp.toFloat()
        val out = ArrayList<Block>()
        fun add(p: Part, l: Int, t: Int, r: Int, b: Int) { out += Block(p, l / w, t / h, r / w, b / h) }
        val left = LayoutStyle.SAFE_H_DP
        val right = screen.widthDp - LayoutStyle.SAFE_H_DP
        var y = LayoutStyle.SAFE_V_DP
        // Top bar: clock left, pills right.
        add(Part.TOP_CLOCK, left, y + 8, left + CLOCK_W_DP, y + LayoutStyle.TOP_BAR_DP - 8)
        for (i in 0 until PILLS) {
            val r = right - i * (PILL_W_DP + PILL_GAP_DP)
            add(Part.TOP_PILL, r - PILL_W_DP, y + 12, r, y + LayoutStyle.TOP_BAR_DP - 12)
        }
        y += LayoutStyle.TOP_BAR_DP
        // Hero area.
        val heroTop = y
        val heroBottom = y + plan.heroHeightDp
        var textLeft = left
        if (plan.hero == HeroMode.ART && plan.artWidthDp > 0) {
            val artTop = heroTop + (plan.heroHeightDp - plan.artHeightDp) / 2
            add(Part.HERO_ART, left, artTop, left + plan.artWidthDp, artTop + plan.artHeightDp)
            textLeft = left + plan.artWidthDp + LayoutStyle.ART_GAP_DP
        }
        if (plan.hero != HeroMode.NONE) {
            val textH = TITLE_H_DP + if (plan.heroSubtitle) 8 + SUBTITLE_H_DP else 0
            val t = heroTop + (plan.heroHeightDp - textH) / 2
            add(Part.HERO_TITLE, textLeft, t, textLeft + (right - textLeft) * 45 / 100, t + TITLE_H_DP)
            if (plan.heroSubtitle) {
                val st = t + TITLE_H_DP + 8
                add(Part.HERO_SUBTITLE, textLeft, st, textLeft + (right - textLeft) * 28 / 100, st + SUBTITLE_H_DP)
            }
        }
        y = heroBottom + TileSpec.PAD_DP
        // Tiles: the cards of the rows on screen (labels are not drawn).
        val rows = plan.visibleRows
        val cols = plan.columns
        for (r in 0 until rows) {
            val top = y + r * plan.rowPitchDp + TileSpec.PAD_DP
            for (c in 0 until cols) {
                val l = LayoutStyle.cardStartDp(plan.dims) + c * plan.pitchDp
                add(if (r == 0 && c == 0) Part.TILE_FOCUSED else Part.TILE, l, top, l + plan.dims.widthDp, top + plan.dims.heightDp)
            }
        }
        y = heroBottom + plan.listHeightDp
        if (plan.stripRowDp > 0) {
            val sTop = y + (plan.stripRowDp - STRIP_H_DP) / 2
            add(Part.STRIP, left, sTop, left + STRIP_W_DP, sTop + STRIP_H_DP)
        }
        return out
    }
}
