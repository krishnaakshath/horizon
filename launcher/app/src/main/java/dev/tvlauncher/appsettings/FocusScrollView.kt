package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView
import dev.tvlauncher.ui.ScrollTarget

/**
 * A ScrollView whose D-pad focus-follow keeps the focused child [marginPx] away from the edges,
 * so a scaled (focused) entry is never clipped. Used for the sidebar (it scrolls when a large
 * group such as Apps & tools is open on a 540 dp tall screen).
 */
internal class FocusScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {
    /** Distance kept between the focused child and the viewport edges, in px. */
    var marginPx: Int = 0

    constructor(context: Context, marginPx: Int) : this(context, null as AttributeSet?) {
        this.marginPx = marginPx
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0) return 0
        val viewport = height - paddingTop - paddingBottom
        val maxScroll = (getChildAt(0).height - viewport).coerceAtLeast(0)
        val d = ScrollTarget.compute(rect.top, rect.bottom, scrollY, scrollY + viewport, marginPx)
        return d.coerceIn(-scrollY, maxScroll - scrollY)
    }
}
