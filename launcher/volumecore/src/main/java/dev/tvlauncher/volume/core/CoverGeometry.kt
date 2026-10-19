package dev.tvlauncher.volume.core

import kotlin.math.roundToInt

/** A screen rectangle in pixels, right/bottom exclusive (like android.graphics.Rect). */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** True when [other] lies completely inside this rectangle. */
    fun contains(other: PxRect): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

/**
 * Where the TV's own volume popup sits, and the panel that covers it (pure, JVM-tested).
 *
 * Measured on a TCL 32S5403AF (Android 11, 1280 x 720 UI, density 1.33) with `dumpsys window
 * windows` while the popup was up: a com.android.systemui window of type SYSTEM_ERROR,
 * gravity RIGHT|CENTER_VERTICAL, x offset 50, frame [1150,227][1230,493] (80 x 266 px), the same
 * frame at every level and when muted. Other resolutions are scaled from that 1280 x 720
 * reference by the smaller of the two axis ratios, keeping the right inset and the vertical
 * centre, so the cover lands on the same place of the picture on a 1080p or 4K UI.
 */
object CoverGeometry {
    const val REF_W = 1280
    const val REF_H = 720

    /** The measured popup frame at the reference size. */
    val TV_POPUP_REF = PxRect(1150, 227, 1230, 493)

    /** Our panel overhangs the popup by this much on every side (reference pixels). */
    const val OVERHANG_REF = 12

    /** Corner radius of our panel (reference pixels): small enough that the corners still cover. */
    const val RADIUS_REF = 22

    /**
     * True where the popup's place is known: TCL's Android TV builds (the popup is TCL's own
     * system UI). Elsewhere the cover would sit next to, not over, the TV's popup, so it is off.
     */
    fun measuredOn(manufacturer: String?, brand: String?): Boolean =
        manufacturer.equals("TCL", ignoreCase = true) || brand.equals("TCL", ignoreCase = true)

    fun scale(screenW: Int, screenH: Int): Float =
        if (screenW <= 0 || screenH <= 0) 0f else minOf(screenW.toFloat() / REF_W, screenH.toFloat() / REF_H)

    /** The TV popup's frame on a [screenW] x [screenH] screen, or null for a nonsense size. */
    fun tvPopup(screenW: Int, screenH: Int): PxRect? {
        val s = scale(screenW, screenH)
        if (s <= 0f) return null
        val ref = TV_POPUP_REF
        val rightInset = ((REF_W - ref.right) * s).roundToInt()
        val w = (ref.width * s).roundToInt()
        val h = (ref.height * s).roundToInt()
        // The reference popup is centred: (227 + 493) / 2 == 360 == 720 / 2.
        val top = ((screenH - h) / 2f).roundToInt()
        val right = screenW - rightInset
        return PxRect(right - w, top, right, top + h)
    }

    /** Our panel: the popup plus [OVERHANG_REF] (scaled) on every side, kept on screen. */
    fun cover(screenW: Int, screenH: Int): PxRect? {
        val p = tvPopup(screenW, screenH) ?: return null
        val o = (OVERHANG_REF * scale(screenW, screenH)).roundToInt().coerceAtLeast(1)
        return PxRect(
            (p.left - o).coerceAtLeast(0),
            (p.top - o).coerceAtLeast(0),
            (p.right + o).coerceAtMost(screenW),
            (p.bottom + o).coerceAtMost(screenH),
        )
    }

    fun radius(screenW: Int, screenH: Int): Float = RADIUS_REF * scale(screenW, screenH)

    /** Room around our panel for its soft shadow (reference pixels). */
    const val SHADOW_REF = 10

    /** The window: the panel plus its shadow room, kept on screen. */
    fun window(screenW: Int, screenH: Int): PxRect? {
        val c = cover(screenW, screenH) ?: return null
        val r = (SHADOW_REF * scale(screenW, screenH)).roundToInt()
        return PxRect(
            (c.left - r).coerceAtLeast(0),
            (c.top - r).coerceAtLeast(0),
            (c.right + r).coerceAtMost(screenW),
            (c.bottom + r).coerceAtMost(screenH),
        )
    }

    /** [r] moved into the coordinates of [origin] (a window's own pixels). */
    fun relative(r: PxRect, origin: PxRect): PxRect =
        PxRect(r.left - origin.left, r.top - origin.top, r.right - origin.left, r.bottom - origin.top)

    /**
     * True when a rounded panel [cover] with corner [radius] still hides every pixel of [popup],
     * even if the popup had square corners: each popup corner, seen from the matching corner of
     * the panel at (dx, dy), must lie inside the panel's corner arc (centre (r, r), radius r).
     */
    fun cornersCover(cover: PxRect, popup: PxRect, radius: Float): Boolean {
        if (!cover.contains(popup)) return false
        val r = radius.coerceAtLeast(0f)
        val dx = minOf(popup.left - cover.left, cover.right - popup.right).toFloat()
        val dy = minOf(popup.top - cover.top, cover.bottom - popup.bottom).toFloat()
        if (dx >= r || dy >= r) return true
        val ex = r - dx
        val ey = r - dy
        return ex * ex + ey * ey <= r * r
    }
}
