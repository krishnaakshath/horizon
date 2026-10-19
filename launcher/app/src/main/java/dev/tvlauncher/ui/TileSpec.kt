package dev.tvlauncher.ui

/** Pure-Kotlin tile constants so they can be asserted on the JVM. */
object TileSpec {
    /** Card (art) size: 16:9. */
    const val WIDTH_DP = 192
    const val HEIGHT_DP = 108
    /** Transparent room around the card for the focus ring and shadow. */
    const val PAD_DP = 8
    const val LABEL_GAP_DP = 10
    const val LABEL_HEIGHT_DP = 20
    /** Visual gap between neighbouring cards. */
    const val GAP_DP = 24
    const val CORNER_DP = 18
    const val RING_OFFSET_DP = 4
    const val RING_STROKE_DP = 2
    const val ICON_DP = 60
    const val FOCUS_SCALE = 1.10f
    const val FOCUS_MS = 160L
    const val REST_ELEVATION_DP = 2
    const val FOCUS_ELEVATION_DP = 12
    const val REST_ALPHA = 0.85f
    const val FOCUS_ALPHA = 1.0f
    const val PARALLAX_BG_DP = 2
    const val PARALLAX_FG_DP = 4
    /** Spec open question 2: settle on-device in Task 12. */
    const val PARALLAX_IN_LITE = false

    const val VIEW_WIDTH_DP = WIDTH_DP + 2 * PAD_DP
    const val VIEW_HEIGHT_DP = PAD_DP + HEIGHT_DP + LABEL_GAP_DP + LABEL_HEIGHT_DP + PAD_DP

    /** App names show only on the focused tile (the hero repeats it large); hidden at rest. */
    const val LABEL_REST_ALPHA = 0f
    const val LABEL_FOCUS_ALPHA = 1f

    fun labelAlpha(focused: Boolean): Float = if (focused) LABEL_FOCUS_ALPHA else LABEL_REST_ALPHA

    const val LABEL_MAX_LINES = 1
    val LABEL_ELLIPSIZE = Ellipsize.END
    const val LABEL_SP = 13

    enum class Ellipsize { NONE, START, MIDDLE, END }

    /** Card sizes for a tile-size multiplier (Settings > Appearance > Tile size); 1f is today's tile. */
    fun dims(scale: Float): TileDims {
        if (scale == 1f) return TileDims(WIDTH_DP, HEIGHT_DP, ICON_DP)
        val w = Math.round(WIDTH_DP * scale / 2f) * 2
        return TileDims(w, Math.round(w * 9f / 16f), Math.round(ICON_DP * scale))
    }
}

/** Size of one tile's card and view in dp; padding, label and gaps do not scale. */
data class TileDims(val widthDp: Int, val heightDp: Int, val iconDp: Int) {
    val viewWidthDp: Int get() = widthDp + 2 * TileSpec.PAD_DP
    val viewHeightDp: Int get() =
        TileSpec.PAD_DP + heightDp + TileSpec.LABEL_GAP_DP + TileSpec.LABEL_HEIGHT_DP + TileSpec.PAD_DP
}
