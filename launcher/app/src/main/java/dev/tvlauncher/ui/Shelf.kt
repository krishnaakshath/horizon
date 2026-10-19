package dev.tvlauncher.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.homelayout.LayoutPlan
import dev.tvlauncher.homelayout.LayoutStyle
import dev.tvlauncher.homelayout.ListKind

/**
 * The app list plus its first-load loader (sun-disc on a line); [list] is the RecyclerView of
 * tiles. One adapter for every home layout style: [apply] swaps the layout manager (a row, two
 * rows or a paged grid) and sizes the list; tiles stay direct children of [list] in every style.
 */
class Shelf(context: Context, private val shelfAdapter: ShelfAdapter, marginH: Int) : FrameLayout(context) {
    private val pad = context.dp(TileSpec.PAD_DP)
    val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = shelfAdapter
        clipToPadding = false
        clipChildren = false
        itemAnimator = null
        setPadding(context.dp(LayoutStyle.listPadStartDp(shelfAdapter.dims)), pad,
            context.dp(LayoutStyle.listPadEndDp(shelfAdapter.dims)), pad)
        // Runs edge to edge so tiles scroll in and peek past the end; tiles are checked on the start edge.
        auditTag(AuditTags.PEEK)
        addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: Rect, v: View, p: RecyclerView, s: RecyclerView.State) {
                out.right = context.dp(TileSpec.DECOR_DP)
            }
        })
    }
    val loader = HorizonLoader(context)

    /** The arrangement [list] is laid out with now. */
    var kind: ListKind = ListKind.ROW
        private set
    private var lanes = 1
    private var heightDp = 0

    init {
        clipChildren = false
        clipToPadding = false
        addView(list, LayoutParams(-1, -1))
        addView(loader, LayoutParams(-2, -2, Gravity.CENTER))
    }

    /** Total list height: tile rows plus the ring/shadow padding above and below. */
    fun heightPx() = if (heightDp > 0) context.dp(heightDp) else context.dp(shelfAdapter.dims.viewHeightDp) + 2 * pad

    /**
     * Arranges [list] for [plan]. [resized]: the card size changed, so views built for the old
     * size are dropped (the adapter is re-attached with an empty pool).
     */
    fun apply(plan: LayoutPlan, resized: Boolean) {
        heightDp = plan.listHeightDp
        list.setPadding(context.dp(LayoutStyle.listPadStartDp(plan.dims)), pad, context.dp(LayoutStyle.listPadEndDp(plan.dims)), pad)
        val lp = list.layoutParams as LayoutParams
        val w = plan.listWidthDp?.let { context.dp(it) } ?: LayoutParams.MATCH_PARENT
        if (lp.width != w) { lp.width = w; list.layoutParams = lp }
        if (kind != plan.kind || lanes != plan.lanes) {
            kind = plan.kind
            lanes = plan.lanes
            list.layoutManager = when (plan.kind) {
                ListKind.ROW -> LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                ListKind.BANDS -> GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
                ListKind.GRID -> GridLayoutManager(context, plan.lanes, GridLayoutManager.VERTICAL, false)
            }
        }
        // Enough recycled tiles for a full screen of the style, so paging does not inflate new ones.
        list.recycledViewPool.setMaxRecycledViews(0, maxOf(DEFAULT_POOL, plan.columns * plan.visibleRows))
        if (resized) {
            list.adapter = null
            list.recycledViewPool.clear()
            list.adapter = shelfAdapter
        }
        requestLayout()
    }

    /** Grid paging: puts adapter position [anchor] at the top of the list. */
    fun scrollToAnchor(anchor: Int) {
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(anchor, 0)
    }

    private companion object {
        const val DEFAULT_POOL = 5
    }
}
