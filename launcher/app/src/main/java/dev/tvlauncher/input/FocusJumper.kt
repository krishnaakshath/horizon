package dev.tvlauncher.input

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.HorizontalScrollView
import android.widget.ScrollView

/**
 * L1/R1 and L2/R2 on any screen: focus jumps a page inside the list or scroller holding it
 * (the home shelf, a Settings page, a panel's rows), using the same focus search the D-pad uses,
 * so lists that load items lazily fill in as the jump walks. A page with nothing to focus
 * (long text) scrolls instead. No list, no jump.
 */
internal object FocusJumper {
    /** L1/R1: a page sideways; in a vertical list (no sideways neighbour) a page up/down. */
    fun page(activity: Activity, forward: Boolean): Boolean {
        val f = activity.currentFocus ?: return false
        if (jump(f, if (forward) View.FOCUS_RIGHT else View.FOCUS_LEFT, horizontal = true)) return true
        return jump(f, if (forward) View.FOCUS_DOWN else View.FOCUS_UP, horizontal = false)
    }

    /** L2/R2: a page up/down; scrolls a page when there is nothing to focus. */
    fun scroll(activity: Activity, forward: Boolean): Boolean {
        val f = activity.currentFocus
        if (f != null && jump(f, if (forward) View.FOCUS_DOWN else View.FOCUS_UP, horizontal = false)) return true
        val dir = if (forward) 1 else -1
        val target = scrollable(activity, dir) ?: return false
        val page = (target.height * PAGE_FRACTION).toInt().coerceAtLeast(1)
        when (target) {
            is ScrollView -> target.smoothScrollBy(0, page * dir)
            else -> target.scrollBy(0, page * dir)
        }
        return true
    }

    private fun jump(f: View, direction: Int, horizontal: Boolean): Boolean {
        val list = listAncestor(f, horizontal) ?: return false
        val steps = FocusJump.pageSteps(
            if (horizontal) list.width else list.height,
            if (horizontal) f.width else f.height
        )
        var cur = f
        for (i in 0 until steps) {
            val next = cur.focusSearch(direction) ?: break
            if (next === cur || !isInside(next, list)) break
            cur = next
        }
        if (cur === f) return false
        return cur.requestFocus(direction)
    }

    /** The nearest list or scroller around [v] that runs along the asked axis. */
    private fun listAncestor(v: View, horizontal: Boolean): ViewGroup? {
        var p = v.parent
        while (p is ViewGroup) {
            when {
                horizontal && p is HorizontalScrollView -> return p
                !horizontal && (p is ScrollView || p is AbsListView) -> return p
                isRecycler(p) && (if (horizontal) p.canScrollHorizontally(1) || p.canScrollHorizontally(-1) || wide(p)
                    else p.canScrollVertically(1) || p.canScrollVertically(-1) || !wide(p)) -> return p
            }
            p = p.parent
        }
        return null
    }

    /** A recycler that cannot scroll right now still has an orientation; guess it from its shape. */
    private fun wide(v: View) = v.width > v.height

    private fun isRecycler(v: View): Boolean {
        var c: Class<*>? = v.javaClass
        while (c != null && c != ViewGroup::class.java) {
            if (c.name == "androidx.recyclerview.widget.RecyclerView") return true
            c = c.superclass
        }
        return false
    }

    private fun isInside(v: View, group: ViewGroup): Boolean {
        var p = v.parent
        while (p != null) {
            if (p === group) return true
            p = p.parent
        }
        return false
    }

    /** What the right stick and an empty-page L2/R2 scroll: around the focus first, else the biggest. */
    fun scrollable(activity: Activity, dir: Int): View? {
        var p: Any? = activity.currentFocus
        while (p is View) {
            if (p.canScrollVertically(dir)) return p
            p = p.parent
        }
        val root = activity.window?.decorView ?: return null
        var best: View? = null
        fun walk(v: View, depth: Int) {
            if (v.visibility != View.VISIBLE || depth > MAX_DEPTH) return
            if (v.canScrollVertically(dir) && (best == null || v.height * v.width > best!!.height * best!!.width)) best = v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        walk(root, 0)
        return best
    }

    private const val PAGE_FRACTION = 0.8f
    private const val MAX_DEPTH = 24
}
