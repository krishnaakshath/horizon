package dev.tvlauncher.homelayout

import dev.tvlauncher.ui.TileDims
import dev.tvlauncher.ui.TileSpec

/** The screen in dp (720p, 1080p and 4K TVs differ by density, so this is what tile counts follow). */
data class ScreenDp(val widthDp: Int, val heightDp: Int) {
    companion object {
        /** The usual TV configuration at any resolution (tvdpi 720p, xhdpi 1080p, xxxhdpi 4K). */
        val TV = ScreenDp(960, 540)
        /** A 720p panel at mdpi. */
        val MDPI_720 = ScreenDp(1280, 720)
        /** A 4K panel at xhdpi. */
        val XHDPI_4K = ScreenDp(1920, 1080)

        fun of(widthPx: Int, heightPx: Int, density: Float): ScreenDp =
            ScreenDp((widthPx / density).toInt(), (heightPx / density).toInt())
    }
}

/** How the app list is laid out by its RecyclerView layout manager. */
enum class ListKind {
    /** One horizontal row (LinearLayoutManager). */
    ROW,
    /** Two horizontal rows sharing one scroll (GridLayoutManager, horizontal, two spans). */
    BANDS,
    /** Rows of [LayoutPlan.lanes] columns scrolling vertically by pages (GridLayoutManager, vertical). */
    GRID
}

/** What the hero area above the apps shows. */
enum class HeroMode { TEXT, ART, NONE }

/**
 * Everything the home screen needs to arrange itself for one style, in dp. Pure; [LayoutStyle.plan]
 * makes it. Heights stack inside the safe area (48 x 27 dp): top bar, hero, list, quick-strip row.
 */
data class LayoutPlan(
    val style: HomeStyle,
    val kind: ListKind,
    val tileScale: Float,
    val dims: TileDims,
    /** Spans of the layout manager: 1 (row), 2 (bands) or the column count (grid). */
    val lanes: Int,
    /** Tiles fully visible across one row. */
    val columns: Int,
    /** Rows visible at once; a grid pages by this many. */
    val visibleRows: Int,
    /** RecyclerView height including the focus-ring padding above and below. */
    val listHeightDp: Int,
    /** Exact RecyclerView width for a grid (so every span is one tile pitch); null = full width. */
    val listWidthDp: Int?,
    val hero: HeroMode,
    val heroSubtitle: Boolean,
    val quickStrip: Boolean,
    /** Height reserved for the quick-strip row (0 = none). */
    val stripRowDp: Int,
    /** Height left for the hero area (it takes the spare height). */
    val heroHeightDp: Int,
    /** Big hero art card (0 x 0 unless [hero] is [HeroMode.ART]). */
    val artWidthDp: Int,
    val artHeightDp: Int,
    val labels: LabelMode
) {
    /** Card-to-card distance across a row. */
    val pitchDp: Int get() = dims.widthDp + TileSpec.GAP_DP

    /** Distance between two rows of tiles (the view already holds the ring padding and label). */
    val rowPitchDp: Int get() = dims.viewHeightDp
}

/** Pure layout strategies: one function from the choices and the screen to a [LayoutPlan]. */
object LayoutStyle {
    const val SAFE_H_DP = 48
    const val SAFE_V_DP = 27
    /** Room for the top bar (clock and pills). */
    const val TOP_BAR_DP = 48
    /** Matches QuickStrip.ROW_DP (asserted in tests). */
    const val STRIP_ROW_DP = 78
    /** The hero title and subtitle need this much; the list shrinks before the hero does. */
    const val HERO_MIN_DP = 96
    const val ART_MIN_DP = 120
    const val ART_GAP_DP = 32
    /** The art is a tile view: ring padding and the (hidden) label slot around its card. */
    const val ART_CHROME_DP = 2 * TileSpec.PAD_DP + TileSpec.LABEL_GAP_DP + TileSpec.LABEL_HEIGHT_DP
    const val GRID_MIN_ROWS = 3
    const val GRID_MAX_ROWS = 4
    const val MIN_SCALE = 0.5f
    private const val SCALE_STEP = 0.05f

    /** Per-style tile scale on top of the user's tile size: compact styles start smaller. */
    fun baseScale(style: HomeStyle): Float = when (style) {
        HomeStyle.SINGLE_ROW, HomeStyle.MINIMAL -> 1f
        HomeStyle.BIG_HERO -> 0.8f
        HomeStyle.TWO_ROWS, HomeStyle.GRID -> 0.75f
    }

    /** How far a compact style may grow on a large screen, relative to the user's tile size. */
    fun growCap(style: HomeStyle): Float = when (style) {
        HomeStyle.GRID -> 1f
        HomeStyle.TWO_ROWS -> 0.85f
        else -> baseScale(style)
    }

    fun kind(style: HomeStyle): ListKind = when (style) {
        HomeStyle.TWO_ROWS -> ListKind.BANDS
        HomeStyle.GRID -> ListKind.GRID
        else -> ListKind.ROW
    }

    fun heroMode(style: HomeStyle): HeroMode = when (style) {
        HomeStyle.SINGLE_ROW, HomeStyle.TWO_ROWS -> HeroMode.TEXT
        HomeStyle.BIG_HERO -> HeroMode.ART
        HomeStyle.GRID, HomeStyle.MINIMAL -> HeroMode.NONE
    }

    /** Whole tiles across [screen] with the left card edge on the safe margin. */
    fun columns(screen: ScreenDp, cardWidthDp: Int): Int {
        val room = screen.widthDp - 2 * (SAFE_H_DP - TileSpec.PAD_DP)
        return maxOf(1, room / (cardWidthDp + TileSpec.GAP_DP))
    }

    fun plan(settings: HomeLayoutSettings, userScale: Float, screen: ScreenDp): LayoutPlan {
        val style = settings.style
        val kind = kind(style)
        val hero = heroMode(style)
        // A grid has no strip row (DOWN on its last row goes to Spotlight); other styles follow the setting.
        val strip = settings.quickStrip && kind != ListKind.GRID
        val stripDp = if (strip) STRIP_ROW_DP else 0
        val inner = screen.heightDp - 2 * SAFE_V_DP - TOP_BAR_DP - stripDp
        val heroNeed = when (hero) {
            HeroMode.TEXT -> HERO_MIN_DP
            HeroMode.ART -> ART_MIN_DP + ART_CHROME_DP
            HeroMode.NONE -> 0
        }
        fun rowsAt(d: TileDims): Int = when (kind) {
            ListKind.ROW -> 1
            ListKind.BANDS -> 2
            ListKind.GRID -> ((inner - heroNeed - 2 * TileSpec.PAD_DP) / d.viewHeightDp).coerceAtMost(GRID_MAX_ROWS)
        }
        fun fits(d: TileDims, rows: Int): Boolean =
            inner - (rows * d.viewHeightDp + 2 * TileSpec.PAD_DP) >= heroNeed && (kind != ListKind.GRID || rows >= GRID_MIN_ROWS)
        // Shrink until the style fits the screen (a small 720p-in-dp screen) ...
        var scale = quantize(userScale * baseScale(style))
        var dims = TileSpec.dims(scale)
        var rows = rowsAt(dims)
        while (!fits(dims, rows) && scale > MIN_SCALE) {
            scale = quantize(scale - SCALE_STEP).coerceAtLeast(MIN_SCALE)
            dims = TileSpec.dims(scale)
            rows = rowsAt(dims)
        }
        // ... and on a large one let compact styles grow toward the user's tile size, so a grid of
        // four rows fills the screen instead of leaving it half empty.
        val cap = quantize(userScale * growCap(style))
        while (kind != ListKind.ROW && scale + SCALE_STEP <= cap + 0.001f) {
            val next = quantize(scale + SCALE_STEP)
            val d = TileSpec.dims(next)
            val r = rowsAt(d)
            val keep = if (kind == ListKind.GRID) rows == GRID_MAX_ROWS && r == GRID_MAX_ROWS else fits(d, r)
            if (!keep || !fits(d, r)) break
            scale = next; dims = d; rows = r
        }
        rows = rows.coerceAtLeast(1)
        val listH = rows * dims.viewHeightDp + 2 * TileSpec.PAD_DP
        val cols = columns(screen, dims.widthDp)
        val heroH = (inner - listH).coerceAtLeast(0)
        val listW = if (kind == ListKind.GRID) 2 * (SAFE_H_DP - TileSpec.PAD_DP) + cols * (dims.widthDp + TileSpec.GAP_DP) else null
        var artW = 0
        var artH = 0
        if (hero == HeroMode.ART) {
            artH = (heroH - ART_CHROME_DP).coerceAtLeast(0)
            artW = artH * 16 / 9
            val maxW = (screen.widthDp - 2 * SAFE_H_DP) * 45 / 100
            if (artW > maxW) { artW = maxW; artH = artW * 9 / 16 }
        }
        return LayoutPlan(
            style = style, kind = kind, tileScale = scale, dims = dims,
            lanes = if (kind == ListKind.GRID) cols else rows,
            columns = cols, visibleRows = rows, listHeightDp = listH, listWidthDp = listW,
            hero = hero, heroSubtitle = settings.heroSubtitle && hero != HeroMode.NONE,
            quickStrip = strip, stripRowDp = stripDp, heroHeightDp = heroH,
            artWidthDp = artW, artHeightDp = artH, labels = settings.labels
        )
    }

    private fun quantize(s: Float): Float = Math.round(s * 100f) / 100f
}
