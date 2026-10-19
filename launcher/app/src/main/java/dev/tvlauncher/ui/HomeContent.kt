package dev.tvlauncher.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import dev.tvlauncher.homelayout.LayoutPlan

/**
 * The home column: top bar, hero area (takes the spare height), the shelf and the quick-strip
 * row under it. Overscan-safe margins are applied here; [applyPlan] sizes the rows for the
 * Home layout style.
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

    /** Row heights for [plan]: the list's height, the strip row (gone when off) and the hero takes the rest. */
    fun applyPlan(plan: LayoutPlan) {
        val s = shelf.layoutParams as LayoutParams
        val h = shelf.heightPx()
        if (s.height != h) { s.height = h; shelf.layoutParams = s }
        strip.visibility = if (plan.stripRowDp > 0) View.VISIBLE else View.GONE
        val st = strip.layoutParams as LayoutParams
        val sh = context.dp(maxOf(plan.stripRowDp, 0))
        if (plan.stripRowDp > 0 && st.height != sh) { st.height = sh; strip.layoutParams = st }
        requestLayout()
    }

    /** Views the cold-start reveal animates; tiles are the visible ones, left to right. */
    fun revealTargets() = RevealTargets(this, topBar, heroBox.hero, shelf.list, strip) {
        (0 until shelf.list.childCount).map { shelf.list.getChildAt(it) }
            .sortedBy { it.left }
            .mapNotNull { (it as? TileView)?.content }
    }
}
