package dev.tvlauncher.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** The horizontal app row plus its first-load dots; [list] is the RecyclerView of tiles. */
class Shelf(context: Context, private val shelfAdapter: ShelfAdapter, marginH: Int) : FrameLayout(context) {
    private val pad = context.dp(TileSpec.PAD_DP)
    val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = shelfAdapter
        clipToPadding = false
        clipChildren = false
        itemAnimator = null
        setPadding(marginH - pad, pad, marginH - pad, pad)
        addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: Rect, v: View, p: RecyclerView, s: RecyclerView.State) {
                out.right = context.dp(TileSpec.GAP_DP) - 2 * pad
            }
        })
    }
    val loader = BouncingDots(context)

    init {
        clipChildren = false
        clipToPadding = false
        addView(list, LayoutParams(-1, -1))
        addView(loader, LayoutParams(-2, -2, Gravity.CENTER))
    }

    /** Total row height: tile view plus the ring/shadow padding above and below. */
    fun heightPx() = context.dp(shelfAdapter.dims.viewHeightDp) + 2 * pad
}
