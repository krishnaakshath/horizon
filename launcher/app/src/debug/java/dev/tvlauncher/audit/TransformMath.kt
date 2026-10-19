package dev.tvlauncher.audit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure scale/translate geometry for the snapshotter (rotation is not modelled). Android's
 * getLocationOnScreen maps only the view's local (0,0) through the view + ancestor matrices, so
 * `left + width` is the UNSCALED size; the on-screen rect is the mapped origin plus the size times the
 * cumulative scale of the view and all its ancestors.
 */
object TransformMath {
    /** Scale + translate: x' = sx*x + tx, y' = sy*y + ty. */
    data class Affine(val sx: Float = 1f, val sy: Float = 1f, val tx: Float = 0f, val ty: Float = 0f) {
        fun map(x: Float, y: Float) = floatArrayOf(sx * x + tx, sy * y + ty)

        /** First apply [inner], then this. */
        fun after(inner: Affine) = Affine(sx * inner.sx, sy * inner.sy, sx * inner.tx + tx, sy * inner.ty + ty)

        fun mapRect(l: Float, t: Float, r: Float, b: Float): IntArray {
            val p = map(l, t); val q = map(r, b)
            return intArrayOf(min(p[0], q[0]).roundToInt(), min(p[1], q[1]).roundToInt(), max(p[0], q[0]).roundToInt(), max(p[1], q[1]).roundToInt())
        }

        companion object {
            /** A view placed at ([left], [top]) in its parent, scaled about its own pivot (view-local coordinates). */
            fun view(left: Float, top: Float, sx: Float, sy: Float, pivotX: Float, pivotY: Float) =
                Affine(sx, sy, left + pivotX - sx * pivotX, top + pivotY - sy * pivotY)
        }
    }

    /** The screen rect of a view of size [w]x[h] whose local origin maps to ([originX], [originY]). */
    fun fromOrigin(originX: Int, originY: Int, w: Int, h: Int, cumulativeScaleX: Float, cumulativeScaleY: Float): IntArray =
        Affine(cumulativeScaleX, cumulativeScaleY, originX.toFloat(), originY.toFloat()).mapRect(0f, 0f, w.toFloat(), h.toFloat())

    /** A single view's rect in parent coordinates, scaled about its pivot. */
    fun rect(left: Int, top: Int, w: Int, h: Int, scaleX: Float, scaleY: Float, pivotX: Float, pivotY: Float): IntArray =
        Affine.view(left.toFloat(), top.toFloat(), scaleX, scaleY, pivotX, pivotY).mapRect(0f, 0f, w.toFloat(), h.toFloat())
}
