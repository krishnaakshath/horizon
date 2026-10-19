package dev.tvlauncher.volume.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator

/**
 * The volume bar: one glass pill with a speaker glyph, a thin level track and the number (or
 * "Muted"). Drawn entirely in [onDraw] (no child views, no bitmaps) so it is cheap on 1 GB TVs.
 * Colours are the launcher's glass tokens (GlassTokens / Theme), copied as constants because the
 * helper is a separate app.
 */
class VolumeBarView(context: Context, private val face: Typeface?) : View(context) {
    private val d = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    var motion: BarMotion = BarMotion.LITE
        set(value) {
            field = value
            elevation = if (value.shadow) dp(SHADOW_DP) else 0f
        }

    private var state: VolumeState? = null
    private var shownFraction = 0f
    private var fillAnim: ValueAnimator? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLASS }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = maxOf(1f, d * 0.75f) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK }
    private val level = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textAlign = Paint.Align.RIGHT
        textSize = dp(17f)
        typeface = face ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()
    private val tmp = RectF()
    private val speaker = Path()

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        setLayerType(LAYER_TYPE_NONE, null)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(WIDTH_DP).toInt(), dp(HEIGHT_DP).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sheen.shader = LinearGradient(0f, 0f, 0f, h * SHEEN_FRACTION, SHEEN_TOP, 0, Shader.TileMode.CLAMP)
        edge.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), EDGE_TOP, EDGE_BOTTOM, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val s = state ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, sheen)
        val e = edge.strokeWidth / 2f
        tmp.set(e, e, w - e, h - e)
        canvas.drawRoundRect(tmp, r - e, r - e, edge)

        val pad = dp(PAD_DP)
        val icon = dp(ICON_DP)
        drawSpeaker(canvas, pad, (h - icon) / 2f, icon, s)

        val label = if (s.muted) "Muted" else s.level.toString()
        val labelRight = w - pad
        text.color = if (s.muted) TEXT_2 else TEXT
        val fm = text.fontMetrics
        canvas.drawText(label, labelRight, h / 2f - (fm.ascent + fm.descent) / 2f, text)

        val trackLeft = pad + icon + dp(GAP_DP)
        val trackRight = labelRight - dp(LABEL_DP) - dp(GAP_DP)
        val th = dp(TRACK_DP)
        tmp.set(trackLeft, (h - th) / 2f, trackRight, (h + th) / 2f)
        canvas.drawRoundRect(tmp, th / 2f, th / 2f, track)
        val f = shownFraction.coerceIn(0f, 1f)
        if (f > 0f) {
            tmp.right = trackLeft + maxOf(th, (trackRight - trackLeft) * f)
            canvas.drawRoundRect(tmp, th / 2f, th / 2f, level)
        }
    }

    /** The 24-unit speaker glyph (ic_volume / ic_volume_off), scaled to [size]. */
    private fun drawSpeaker(c: Canvas, x: Float, y: Float, size: Float, s: VolumeState) {
        val u = size / 24f
        glyph.color = if (s.muted) TEXT_2 else TEXT
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
        if (s.muted) {
            wave.color = TEXT_2
            c.drawLine(x + 16 * u, y + 9 * u, x + 22 * u, y + 15 * u, wave)
            c.drawLine(x + 22 * u, y + 9 * u, x + 16 * u, y + 15 * u, wave)
            return
        }
        wave.color = TEXT
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
        const val WIDTH_DP = 300f
        const val HEIGHT_DP = 56f
        const val PAD_DP = 20f
        const val ICON_DP = 24f
        const val GAP_DP = 16f
        const val LABEL_DP = 52f
        const val TRACK_DP = 4f
        const val SHADOW_DP = 10f

        // GlassTokens / Theme values (the launcher's single glass style).
        const val GLASS = 0xE01E222B.toInt() // DEFAULT_GLASS_RGB at 0.88: readable over any video frame
        const val SHEEN_TOP = 0x0CFFFFFF
        const val SHEEN_FRACTION = 0.42f
        const val EDGE_TOP = 0x2EFFFFFF
        const val EDGE_BOTTOM = 0x08FFFFFF
        const val TEXT = 0xFFF5F7FA.toInt()
        const val TEXT_2 = 0xFFA7B0C0.toInt()
        const val TRACK = 0x40FFFFFF // Theme.TOGGLE_OFF
    }
}
