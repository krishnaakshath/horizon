package dev.tvlauncher.volume.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * The vertical form of our volume bar, built to lie exactly over a TV's own side popup: number on
 * top, a level track in the middle, the speaker glyph at the bottom (the layout people already
 * read there). The look is the launcher's liquid glass: a translucent frosted panel with a light
 * rim and (FULL motion) a soft shadow. Under the glass, and only under the rectangle it must hide
 * ([setGeometry] core), an opaque backing, so nothing of the covered popup shows through while the
 * overhang around it stays translucent. Drawn in [onDraw] only (no child views, no bitmaps).
 */
class VolumeColumnView(context: Context, private val face: Typeface?) : View(context) {
    var motion: BarMotion = BarMotion.LITE
        set(value) {
            field = value
            invalidate()
        }

    private val panel = RectF()
    private val core = RectF()
    private var radius = 0f
    private var unit = 1f

    /** [panel] and [core] in this view's pixels; [radius] of the panel; [scale] vs a 720p UI. */
    fun setGeometry(panel: PxRect, core: PxRect, radius: Float, scale: Float) {
        this.panel.set(panel.left.toFloat(), panel.top.toFloat(), panel.right.toFloat(), panel.bottom.toFloat())
        this.core.set(core.left.toFloat(), core.top.toFloat(), core.right.toFloat(), core.bottom.toFloat())
        this.radius = radius
        this.unit = if (scale > 0f) scale else 1f
        rebuildShaders()
        invalidate()
    }

    private var state: VolumeState? = null
    private var shownFraction = 0f
    private var fillAnim: ValueAnimator? = null

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHADOW_BASE }
    private val backing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BACKING }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLASS }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VolumeBarView.TRACK }
    private val level = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = VolumeBarView.TEXT }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = face ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val tmp = RectF()
    private val speaker = Path()
    private val p = RectF()
    private val c = RectF()

    /**
     * Badge form while muted: TCL keeps a small mute badge (the bottom square of its popup
     * window) on screen for as long as the sound is muted, so we keep only that square covered.
     */
    var compact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    fun show(s: VolumeState) {
        val target = if (s.muted) 0f else s.fraction
        val first = state == null
        state = s
        fillAnim?.cancel()
        if (first || motion.fillMs == 0L) {
            shownFraction = target
        } else {
            fillAnim = ValueAnimator.ofFloat(shownFraction, target).apply {
                duration = motion.fillMs
                interpolator = DecelerateInterpolator()
                addUpdateListener { shownFraction = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        contentDescription = if (s.muted) "Muted" else "Volume ${s.level}"
        invalidate()
    }

    fun clear() {
        fillAnim?.cancel()
        state = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (panel.isEmpty) panel.set(0f, 0f, w.toFloat(), h.toFloat())
        rebuildShaders()
    }

    private fun rebuildShaders() {
        if (panel.isEmpty) return
        val top = panel.top
        val h = panel.height()
        // Frost: a light top sheen fading out, and a rim brighter at the top (light from above).
        sheen.shader = LinearGradient(0f, top, 0f, top + h * 0.45f, SHEEN_TOP, 0, Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, top, 0f, top + h, RIM_TOP, RIM_BOTTOM, Shader.TileMode.CLAMP)
        rim.strokeWidth = maxOf(1f, 1.25f * unit)
        if (Build.VERSION.SDK_INT >= 28) shadow.setShadowLayer(8f * unit, 0f, 3f * unit, SHADOW)
    }

    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        if (panel.isEmpty) return
        p.set(panel)
        c.set(core)
        if (compact) {
            p.top = maxOf(panel.top, panel.bottom - panel.width())
            if (!core.isEmpty) c.top = maxOf(core.top, core.bottom - core.width())
        }
        val r = radius.coerceIn(0f, p.width() / 2f)
        // Soft shadow (FULL only; hardware shadow layers need Android 9+).
        if (motion.shadow && Build.VERSION.SDK_INT >= 28) canvas.drawRoundRect(p, r, r, shadow)
        // Opaque backing exactly under what must be hidden, then the translucent glass on top.
        if (!c.isEmpty) {
            // Nearly square, so even square corners of the covered window stay hidden (they lie
            // inside the panel's arc: CoverGeometry.cornersCover).
            val cr = 3f * unit
            canvas.drawRoundRect(c, cr, cr, backing)
        }
        canvas.drawRoundRect(p, r, r, glass)
        canvas.drawRoundRect(p, r, r, sheen)
        val e = rim.strokeWidth / 2f
        tmp.set(p.left + e, p.top + e, p.right - e, p.bottom - e)
        canvas.drawRoundRect(tmp, r - e, r - e, rim)
        if (compact) {
            val icon = p.width() * 0.42f
            drawSpeaker(canvas, p.centerX() - icon / 2f, p.centerY() - icon / 2f, icon, s)
            return
        }

        // Proportions from the panel width so the column scales with what it covers.
        val w = panel.width()
        val x0 = panel.left
        val cx = x0 + w / 2f
        val pad = w * 0.20f
        val label = if (s.muted) "Muted" else s.level.toString()
        text.textSize = if (s.muted) w * 0.19f else w * 0.30f
        text.color = if (s.muted) VolumeBarView.TEXT_2 else VolumeBarView.TEXT
        val fm = text.fontMetrics
        val labelBaseline = panel.top + pad * 0.8f - fm.ascent
        canvas.drawText(label, cx, labelBaseline, text)

        val icon = w * 0.36f
        val iconTop = panel.bottom - pad * 0.8f - icon
        drawSpeaker(canvas, cx - icon / 2f, iconTop, icon, s)

        val tw = maxOf(2f, w * 0.07f)
        val trackTop = labelBaseline + fm.descent + pad * 0.7f
        val trackBottom = iconTop - pad * 0.7f
        if (trackBottom - trackTop > tw) {
            tmp.set(cx - tw / 2f, trackTop, cx + tw / 2f, trackBottom)
            canvas.drawRoundRect(tmp, tw / 2f, tw / 2f, track)
            val f = shownFraction.coerceIn(0f, 1f)
            if (f > 0f) {
                tmp.top = trackBottom - maxOf(tw, (trackBottom - trackTop) * f)
                canvas.drawRoundRect(tmp, tw / 2f, tw / 2f, level)
            }
        }
    }

    /** The 24-unit speaker glyph of [VolumeBarView], scaled to [size]. */
    private fun drawSpeaker(c: Canvas, x: Float, y: Float, size: Float, s: VolumeState) {
        val u = size / 24f
        val col = if (s.muted) VolumeBarView.TEXT_2 else VolumeBarView.TEXT
        glyph.color = col
        speaker.reset()
        speaker.moveTo(x + 3 * u, y + 9 * u)
        speaker.lineTo(x + 3 * u, y + 15 * u)
        speaker.lineTo(x + 7 * u, y + 15 * u)
        speaker.lineTo(x + 12 * u, y + 20 * u)
        speaker.lineTo(x + 12 * u, y + 4 * u)
        speaker.lineTo(x + 7 * u, y + 9 * u)
        speaker.close()
        c.drawPath(speaker, glyph)
        wave.strokeWidth = 2f * u
        wave.color = col
        if (s.muted) {
            c.drawLine(x + 16 * u, y + 9 * u, x + 22 * u, y + 15 * u, wave)
            c.drawLine(x + 22 * u, y + 9 * u, x + 16 * u, y + 15 * u, wave)
            return
        }
        val waves = when {
            s.level <= 0 -> 0
            s.fraction < 0.5f -> 1
            else -> 2
        }
        val cx = x + 12 * u
        val cy = y + 12 * u
        if (waves >= 1) {
            tmp.set(cx - 4 * u, cy - 4 * u, cx + 4 * u, cy + 4 * u)
            c.drawArc(tmp, -55f, 110f, false, wave)
        }
        if (waves >= 2) {
            tmp.set(cx - 8 * u, cy - 8 * u, cx + 8 * u, cy + 8 * u)
            c.drawArc(tmp, -55f, 110f, false, wave)
        }
    }

    override fun hasOverlappingRendering() = false

    companion object {
        /** Translucent glass (the launcher's glass RGB at 0.80): video tints through the overhang. */
        const val GLASS = 0xCC1E222B.toInt()

        /** Opaque backing under the covered rectangle, the colour the glass gives over black. */
        const val BACKING = 0xFF181B22.toInt()
        const val SHEEN_TOP = 0x1AFFFFFF
        const val RIM_TOP = 0x59FFFFFF
        const val RIM_BOTTOM = 0x14FFFFFF
        const val SHADOW = 0x73000000
        const val SHADOW_BASE = 0xFF000000.toInt()
    }
}
