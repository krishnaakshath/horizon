package dev.tvlauncher.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * The home column: top bar, hero area (takes the spare height), the shelf and the quick-strip
 * row under it. Overscan-safe margins are applied here.
 */
class HomeContent(
    context: Context,
    private val topBar: View,
    private val heroBox: HeroBox,
    private val shelf: Shelf,
    private val strip: View
) : LinearLayout(context) {
    // Alpha fades on this full-screen container must not allocate an offscreen layer (LITE).
    override fun hasOverlappingRendering() = false

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, context.dp(Theme.MARGIN_V), 0, context.dp(Theme.MARGIN_V))
        val h = context.dp(Theme.MARGIN_H)
        addView(topBar, LayoutParams(-1, -2).apply { marginStart = h; marginEnd = h })
        addView(heroBox, LayoutParams(-1, 0, 1f).apply { marginStart = h; marginEnd = h })
        addView(shelf, LayoutParams(-1, shelf.heightPx()))
        addView(strip, LayoutParams(-1, context.dp(QuickStrip.ROW_DP)))
    }

    /** Views the cold-start reveal animates; tiles are the visible ones, left to right. */
    fun revealTargets() = RevealTargets(this, topBar, heroBox.hero, shelf.list, strip) {
        (0 until shelf.list.childCount).map { shelf.list.getChildAt(it) }
            .sortedBy { it.left }
            .mapNotNull { (it as? TileView)?.content }
    }
}
