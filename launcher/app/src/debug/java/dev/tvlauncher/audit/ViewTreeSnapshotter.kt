package dev.tvlauncher.audit

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
 * Bounds: getLocationOnScreen maps the view's local origin through every view/ancestor matrix, but
 * width/height are unscaled, so the rect is origin + size × the CUMULATIVE scale of the view and its
 * ancestors ([TransformMath.fromOrigin]; a 1.1× focus-scaled tile comes out enlarged around its pivot).
 * Rotation is not modelled. `visible` is "this view and all ancestors are VISIBLE (and the window is
 * visible)", `alpha` the product of all alphas, `bgAlpha` the background fill's alpha × `alpha`
 * ([fill]). Subclass names get a base hint appended (`com.x.Foo|ImageView`) so the engine's
 * class-name rules see through custom views.
 */
object ViewTreeSnapshotter {
    const val MAX_NODES = 6000
    private const val MAX_TEXT = 200

    class Snapshot(val nodes: List<Node>, val notes: List<String>, val screenW: Int, val screenH: Int, val density: Float, val windows: Int)

    fun snapshot(root: View): List<Node> = Walker().apply { walk(root, -1, 0, 1f, true, 1f, 1f) }.nodes

    /**
     * Alpha of a background's fill (0 = none) and whether it could be read. ColorDrawable and
     * GradientDrawable colours are read directly; layers combine (max); otherwise getOpacity():
     * OPAQUE = 1, TRANSPARENT = 0, anything else = unknown.
     */
    fun fill(d: Drawable?, depth: Int = 0): Pair<Float, Boolean> {
        if (d == null) return 0f to true
        if (depth > 4) return 1f to false
        val cur = try { d.current ?: d } catch (t: Throwable) { d }
        return try {
            when (cur) {
                is AuditOpacity -> cur.auditAlpha.coerceIn(0f, 1f) * (cur.alpha / 255f) to true
                is ColorDrawable -> Color.alpha(cur.color) / 255f to true
                is GradientDrawable -> {
                    val colors = cur.colors
                    val solid = cur.color
                    val a = when {
                        colors != null && colors.isNotEmpty() -> colors.maxOf { Color.alpha(it) } / 255f
                        solid != null -> Color.alpha(solid.getColorForState(cur.state, solid.defaultColor)) / 255f
                        else -> 0f
                    }
                    a * (cur.alpha / 255f) to true
                }
                is LayerDrawable -> {
                    var a = 0f
                    var known = true
                    for (i in 0 until cur.numberOfLayers) {
                        if (cur.getId(i) == android.R.id.mask) continue
                        val (la, lk) = fill(cur.getDrawable(i), depth + 1)
                        a = maxOf(a, la); known = known && lk
                    }
                    a to known
                }
                else -> when (cur.opacity) {
                    PixelFormat.OPAQUE -> 1f to true
                    PixelFormat.TRANSPARENT -> 0f to true
                    else -> 1f to false
                }
            }
        } catch (t: Throwable) {
            1f to false
        }
    }

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
            w.walk(root, -1, windows++, 1f, true, 1f, 1f)
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

        fun walk(v: View, parent: Int, z: Int, parentAlpha: Float, parentVisible: Boolean, parentSx: Float, parentSy: Float) {
            if (nodes.size >= MAX_NODES) return
            val id = nodes.size
            v.getLocationOnScreen(loc)
            val visible = parentVisible && v.visibility == View.VISIBLE
            val alpha = parentAlpha * v.alpha
            val sx = parentSx * v.scaleX
            val sy = parentSy * v.scaleY
            val r = TransformMath.fromOrigin(loc[0], loc[1], v.width, v.height, sx, sy)
            val (bg, bgKnown) = fill(v.background)
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
                left = r[0], top = r[1], right = r[2], bottom = r[3],
                visible = visible, alpha = alpha, clipChildren = (v as? ViewGroup)?.clipChildren ?: false,
                text = text, textSizePx = size, maxLines = maxLines, lineCount = lines, ellipsized = ellipsized,
                focusable = v.isFocusable, tag = tag(v), z = z, bgAlpha = bg * alpha, bgKnown = bgKnown,
            )
            if (v is ViewGroup) for (i in 0 until v.childCount) {
                val child = v.getChildAt(i) ?: continue
                walk(child, id, i, alpha, visible, sx, sy)
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
