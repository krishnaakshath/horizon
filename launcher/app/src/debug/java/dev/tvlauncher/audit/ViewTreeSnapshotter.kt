package dev.tvlauncher.audit

import android.app.Activity
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Turns live view hierarchies into [Node]s. MUST run on the main thread. Debug builds only.
 *
 * Bounds come from getLocationOnScreen + width/height. `visible` is "this view and all ancestors are
 * VISIBLE (and the window is visible)", `alpha` the product of all alphas. Subclass names get a base
 * hint appended (`com.x.Foo|ImageView`) so the engine's class-name rules see through custom views.
 */
object ViewTreeSnapshotter {
    const val MAX_NODES = 6000
    private const val MAX_TEXT = 200

    class Snapshot(val nodes: List<Node>, val notes: List<String>, val screenW: Int, val screenH: Int, val density: Float, val windows: Int)

    fun snapshot(root: View): List<Node> = Walker().apply { walk(root, -1, 0, 1f, true) }.nodes

    /** The activity's window plus any other visible window of this process (dialogs, popups). */
    fun snapshotActivity(activity: Activity): Snapshot {
        val notes = ArrayList<String>()
        val roots = LinkedHashSet<View>()
        roots += activity.window.decorView
        for (v in otherRoots(notes)) roots += v
        val w = Walker()
        var windows = 0
        for (root in roots) {
            val shown = try { root.isAttachedToWindow && root.windowVisibility == View.VISIBLE } catch (t: Throwable) { false }
            if (!shown) continue
            w.walk(root, -1, windows++, 1f, true)
            if (w.nodes.size >= MAX_NODES) { notes += "node cap $MAX_NODES reached"; break }
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(dm)
        return Snapshot(w.nodes, notes, dm.widthPixels, dm.heightPixels, dm.density, windows)
    }

    /** Other window roots via the hidden WindowManagerGlobal; reflection may be blocked — then skip with a note. */
    private fun otherRoots(notes: MutableList<String>): List<View> {
        try {
            val cls = Class.forName("android.view.WindowManagerGlobal")
            val g = cls.getMethod("getInstance").invoke(null)
            try {
                @Suppress("UNCHECKED_CAST")
                val names = cls.getMethod("getViewRootNames").invoke(g) as Array<String>
                val get = cls.getMethod("getRootView", String::class.java)
                return names.mapNotNull { n -> try { get.invoke(g, n) as? View } catch (t: Throwable) { null } }
            } catch (t: Throwable) {
                val f = cls.getDeclaredField("mViews").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                return (f.get(g) as? List<View>)?.toList().orEmpty()
            }
        } catch (t: Throwable) {
            notes += "other windows skipped: ${t.javaClass.simpleName}"
            return emptyList()
        }
    }

    private class Walker {
        val nodes = ArrayList<Node>()
        private val loc = IntArray(2)

        fun walk(v: View, parent: Int, z: Int, parentAlpha: Float, parentVisible: Boolean) {
            if (nodes.size >= MAX_NODES) return
            val id = nodes.size
            v.getLocationOnScreen(loc)
            val visible = parentVisible && v.visibility == View.VISIBLE
            val alpha = parentAlpha * v.alpha
            val tv = v as? TextView
            var text: String? = null
            var size = 0f
            var maxLines = Int.MAX_VALUE
            var lines = 0
            var ellipsized = false
            if (tv != null) {
                text = (tv.text?.toString()?.ifEmpty { null } ?: tv.hint?.toString())?.take(MAX_TEXT)
                size = tv.textSize
                maxLines = tv.maxLines
                lines = tv.lineCount
                val layout = tv.layout
                if (layout != null) for (i in 0 until layout.lineCount) if (layout.getEllipsisCount(i) > 0) { ellipsized = true; break }
            }
            nodes += Node(
                id = id, parent = parent, cls = cls(v), name = name(v),
                left = loc[0], top = loc[1], right = loc[0] + v.width, bottom = loc[1] + v.height,
                visible = visible, alpha = alpha, clipChildren = (v as? ViewGroup)?.clipChildren ?: false,
                text = text, textSizePx = size, maxLines = maxLines, lineCount = lines, ellipsized = ellipsized,
                focusable = v.isFocusable, tag = tag(v), z = z,
            )
            if (v is ViewGroup) for (i in 0 until v.childCount) {
                val child = v.getChildAt(i) ?: continue
                walk(child, id, i, alpha, visible)
            }
        }

        private fun cls(v: View): String {
            val n = v.javaClass.name
            val base = when (v) {
                is ScrollView, is HorizontalScrollView -> "ScrollView"
                is RecyclerView -> "RecyclerView"
                is AbsListView -> "ListView"
                is ImageView -> "ImageView"
                is Button -> "Button"
                is TextView -> "TextView"
                else -> null
            }
            return if (base == null || n.contains(base)) n else "$n|$base"
        }

        private fun name(v: View): String? {
            val entry = if (v.id != View.NO_ID && v.id > 0) try { "@id/" + v.resources.getResourceEntryName(v.id) } catch (t: Throwable) { null } else null
            val cd = v.contentDescription?.toString()?.take(60)
            return listOfNotNull(entry, cd).joinToString(" ").ifEmpty { null }
        }

        private fun tag(v: View): String? {
            val keyed = try { v.getTag(AuditTags.KEY) as? String } catch (t: Throwable) { null }
            val plain = (v.tag as? String)?.takeIf { it.startsWith("audit:") }
            return listOfNotNull(keyed, plain).joinToString(" ").ifEmpty { null }
        }
    }
}
