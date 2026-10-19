package dev.tvlauncher.browser.cursor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Draws the pointer (all original Canvas art): ARROW (modern pointer, soft shadow), DOT, RING,
 * HALO and CLASSIC, plus the state overlays (link ring, i-beam, busy arc, disabled badge),
 * long-press arc and click ripple. All paints/paths are built when settings change; draw()
 * allocates nothing.
 */
class CursorRenderer(private val density: Float) {
    var settings = CursorSettings(); private set
    private var sizePx = 0f
    private var fillColor = -1
    private var accent = ACCENT

    // Animated amounts (0..1), driven by the owning view.
    var linkAmt = 0f
    var textAmt = 0f
    var busyAmt = 0f
    var disabledAmt = 0f
    var scrollAmt = 0f
    var pop = 1f
    var pressAmt = 0f
    var spin = 0f
    var longProgress = 0f
    var ripple = -1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val modern = Path()
    private val classic = Path()
    private val unitModern = floatArrayOf(0f, 0f, 0f, 0.9f, 0.27f, 0.68f, 0.64f, 0.64f)
    private val unitClassic = floatArrayOf(0f, 0f, 0f, 0.84f, 0.22f, 0.65f, 0.37f, 0.98f, 0.51f, 0.92f, 0.36f, 0.6f, 0.62f, 0.6f)
    private val arc = RectF()

    init { apply(CursorSettings()) }

    fun apply(s: CursorSettings) {
        settings = s
        sizePx = s.sizeDp * density
        val a = (s.opacity * 255).toInt().coerceIn(0, 255)
        fillColor = (s.color and 0x00FFFFFF) or (a shl 24)
        accent = if (s.color == -1) ACCENT else s.color or (0xFF shl 24)
        build(modern, unitModern)
        build(classic, unitClassic)
        rim.strokeWidth = 1.4f * density
        rim.color = (0xCC shl 24) or 0x0B0D12
        stroke.strokeWidth = 2f * density
        shadow.color = 0x55000000
        halo.shader = RadialGradient(0f, 0f, sizePx * 0.62f,
            intArrayOf((accentOrColor() and 0x00FFFFFF) or (0x99 shl 24), (accentOrColor() and 0x00FFFFFF) or (0x33 shl 24), 0),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    private fun accentOrColor() = if (settings.color == -1) ACCENT else settings.color

    private fun build(p: Path, unit: FloatArray) {
        p.reset()
        val h = sizePx
        p.moveTo(unit[0] * h, unit[1] * h)
        var i = 2
        while (i < unit.size) { p.lineTo(unit[i] * h, unit[i + 1] * h); i += 2 }
        p.close()
    }

    /** The pointer's visual reach around (x, y), for invalidation. */
    val reach: Float get() = sizePx * 2.2f

    fun draw(c: Canvas, x: Float, y: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val s = sizePx * pop * (1f - 0.15f * pressAmt)
        val ga = (alpha * 255).toInt()
        val base = 1f - maxOf(textAmt, linkAmt * 0.8f, disabledAmt * 0.3f)
        c.save()
        c.translate(x, y)
        if (base > 0.02f) drawBase(c, s, (ga * base).toInt())
        if (linkAmt > 0.01f) {
            val r = s * 0.36f * (1f + 0.35f * linkAmt)
            fill.color = withAlpha(accent, (0.22f * linkAmt * ga).toInt())
            c.drawCircle(0f, 0f, r, fill)
            stroke.color = withAlpha(accent, (linkAmt * ga).toInt())
            c.drawCircle(0f, 0f, r, stroke)
            fill.color = withAlpha(fillColor, (linkAmt * ga).toInt())
            c.drawCircle(0f, 0f, 2.6f * density, fill)
        }
        if (textAmt > 0.01f) {
            val h = s * 0.42f * (0.6f + 0.4f * textAmt)
            val w = s * 0.14f
            val ta = (textAmt * ga).toInt()
            rim.color = withAlpha(0x0B0D12, (ta * 0.8f).toInt())
            rim.strokeWidth = 4.2f * density
            ibeam(c, h, w, rim)
            stroke.color = withAlpha(fillColor, ta)
            stroke.strokeWidth = 2f * density
            ibeam(c, h, w, stroke)
            rim.strokeWidth = 1.4f * density
            rim.color = (0xCC shl 24) or 0x0B0D12
        }
        if (busyAmt > 0.01f) {
            val r = s * 0.5f
            arc.set(-r, -r, r, r)
            stroke.color = withAlpha(accent, (busyAmt * ga).toInt())
            c.drawArc(arc, spin, 100f, false, stroke)
        }
        if (disabledAmt > 0.01f) {
            val r = s * 0.16f
            val bx = s * 0.42f; val by = s * 0.42f
            stroke.color = withAlpha(0xF87171, (disabledAmt * ga).toInt())
            c.drawCircle(bx, by, r, stroke)
            c.drawLine(bx - r * 0.7f, by + r * 0.7f, bx + r * 0.7f, by - r * 0.7f, stroke)
        }
        if (longProgress > 0f) {
            val r = s * 0.6f
            arc.set(-r, -r, r, r)
            stroke.color = withAlpha(accent, ga)
            c.drawArc(arc, -90f, 360f * longProgress, false, stroke)
        }
        if (ripple >= 0f) {
            val r = s * 0.3f + s * 1.1f * ripple
            stroke.color = withAlpha(accent, ((1f - ripple) * ga).toInt())
            c.drawCircle(0f, 0f, r, stroke)
        }
        c.restore()
    }

    private fun ibeam(c: Canvas, h: Float, w: Float, p: Paint) {
        c.drawLine(0f, -h, 0f, h, p)
        c.drawLine(-w, -h, w, -h, p)
        c.drawLine(-w, h, w, h, p)
    }

    private fun drawBase(c: Canvas, s: Float, a: Int) {
        val k = s / sizePx
        when (settings.style) {
            CursorStyle.ARROW, CursorStyle.CLASSIC -> {
                val p = if (settings.style == CursorStyle.ARROW) modern else classic
                c.save()
                c.scale(k, k)
                shadow.alpha = (a * 0.33f).toInt()
                c.translate(0.05f * sizePx, 0.08f * sizePx); c.drawPath(p, shadow)
                c.translate(0.03f * sizePx, 0.03f * sizePx); shadow.alpha = (a * 0.15f).toInt(); c.drawPath(p, shadow)
                c.translate(-0.08f * sizePx, -0.11f * sizePx)
                fill.color = withAlpha(fillColor, a * (fillColor ushr 24) / 255)
                c.drawPath(p, fill)
                rim.alpha = (a * 0.8f).toInt()
                c.drawPath(p, rim)
                c.restore()
            }
            CursorStyle.DOT -> {
                fill.color = withAlpha(fillColor, (a * 0.42f).toInt())
                c.drawCircle(0f, 0f, s * 0.36f, fill)
                rim.alpha = (a * 0.45f).toInt(); rim.strokeWidth = 3.4f * density
                c.drawCircle(0f, 0f, s * 0.36f, rim)
                rim.strokeWidth = 1.4f * density
                stroke.color = withAlpha(fillColor, (a * 0.9f).toInt()); stroke.strokeWidth = 1.5f * density
                c.drawCircle(0f, 0f, s * 0.36f, stroke)
                stroke.strokeWidth = 2f * density
                fill.color = withAlpha(fillColor, a)
                c.drawCircle(0f, 0f, maxOf(2f * density, s * 0.08f), fill)
            }
            CursorStyle.RING -> {
                rim.alpha = (a * 0.5f).toInt(); rim.strokeWidth = 3.6f * density
                c.drawCircle(0f, 0f, s * 0.34f, rim)
                rim.strokeWidth = 1.4f * density
                stroke.color = withAlpha(fillColor, a)
                c.drawCircle(0f, 0f, s * 0.34f, stroke)
                fill.color = withAlpha(fillColor, a)
                c.drawCircle(0f, 0f, maxOf(1.8f * density, s * 0.06f), fill)
            }
            CursorStyle.HALO -> {
                c.save(); c.scale(k, k)
                halo.alpha = a
                c.drawCircle(0f, 0f, sizePx * 0.62f, halo)
                c.restore()
                fill.color = withAlpha(fillColor, a)
                c.drawCircle(0f, 0f, s * 0.15f, fill)
                rim.alpha = (a * 0.6f).toInt()
                c.drawCircle(0f, 0f, s * 0.15f, rim)
            }
        }
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    companion object { const val ACCENT = 0xFF5B8CFF.toInt() }
}
