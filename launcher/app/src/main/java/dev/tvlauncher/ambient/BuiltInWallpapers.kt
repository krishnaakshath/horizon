package dev.tvlauncher.ambient

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Original procedural wallpapers ([WallpaperCatalog]) drawn once with Canvas gradients/shapes into
 * a single bitmap (no image assets, no per-frame work). The "Anime" collection evokes moods only
 * (energy auras, cursed-energy swirls, a walled city at dusk...): no characters, names or logos.
 * Rendering a frame is a few full-screen gradient fills plus a few hundred small shapes, all
 * seeded so every render is identical; call it off the main thread.
 */
object BuiltInWallpapers {
    val ALL: List<WallpaperCatalog.Meta> get() = WallpaperCatalog.ALL

    const val DEFAULT_ACCENT = 0xFF3A6BFF.toInt()

    fun meta(id: String): WallpaperCatalog.Meta = WallpaperCatalog.meta(id)

    /** Renders [id] at [w]x[h]. [accent] tints "mono"'s bloom (e.g. a brand color). */
    fun render(id: String, w: Int, h: Int, accent: Int = DEFAULT_ACCENT, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val bw = max(1, w); val bh = max(1, h)
        val bmp = Bitmap.createBitmap(bw, bh, config)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val m = meta(id)
        c.drawColor(m.baseColor)
        when (m.id) {
            "aurora" -> aurora(c, p, bw.toFloat(), bh.toFloat())
            "dusk" -> dusk(c, p, bw.toFloat(), bh.toFloat())
            "ocean" -> ocean(c, p, bw.toFloat(), bh.toFloat())
            "graphite" -> graphite(c, p, bw.toFloat(), bh.toFloat())
            "nebula" -> nebula(c, p, bw.toFloat(), bh.toFloat())
            "golden_aura" -> AnimeWallpapers.goldenAura(c, p, bw.toFloat(), bh.toFloat())
            "cursed_night" -> AnimeWallpapers.cursedNight(c, p, bw.toFloat(), bh.toFloat())
            "beyond_walls" -> AnimeWallpapers.beyondWalls(c, p, bw.toFloat(), bh.toFloat())
            "leaf_dusk" -> AnimeWallpapers.leafDusk(c, p, bw.toFloat(), bh.toFloat())
            "petal_night" -> AnimeWallpapers.petalNight(c, p, bw.toFloat(), bh.toFloat())
            "neon_rain" -> AnimeWallpapers.neonRain(c, p, bw.toFloat(), bh.toFloat())
            "moonlit_sea" -> AnimeWallpapers.moonlitSea(c, p, bw.toFloat(), bh.toFloat())
            else -> mono(c, p, bw.toFloat(), bh.toFloat(), accent)
        }
        return bmp
    }

    internal fun glow(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        val inner = (color and 0x00FFFFFF) or (alpha shl 24)
        val mid = (color and 0x00FFFFFF) or ((alpha * 0.35f).toInt() shl 24)
        p.shader = RadialGradient(cx, cy, max(1f, r), intArrayOf(inner, mid, 0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
    }

    internal fun vertical(c: Canvas, p: Paint, w: Float, h: Float, colors: IntArray, stops: FloatArray?) {
        p.shader = LinearGradient(0f, 0f, 0f, h, colors, stops, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    internal fun vignette(c: Canvas, p: Paint, w: Float, h: Float, strength: Int) {
        val r = max(w, h) * 0.75f
        p.shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0, 0, strength shl 24), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    /** Soft multi-color mesh: four overlapping radial glows over deep navy. */
    private fun aurora(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        glow(c, p, w * 0.18f, h * 0.22f, s * 0.95f, 0xFF1FD1A5.toInt(), 150)
        glow(c, p, w * 0.62f, h * 0.10f, s * 0.85f, 0xFF7B5CFF.toInt(), 170)
        glow(c, p, w * 0.92f, h * 0.65f, s * 0.80f, 0xFFE055B0.toInt(), 120)
        glow(c, p, w * 0.40f, h * 0.95f, s * 0.90f, 0xFF2F7BFF.toInt(), 130)
        vertical(c, p, w, h, intArrayOf(0x00000000, 0x00000000, 0x8C05070F.toInt()), floatArrayOf(0f, 0.5f, 1f))
    }

    /** Violet-to-ember sky with a low sun glow and two soft horizon bands. */
    private fun dusk(c: Canvas, p: Paint, w: Float, h: Float) {
        vertical(c, p, w, h, intArrayOf(0xFF140F33.toInt(), 0xFF3E2259.toInt(), 0xFF8E3F62.toInt(), 0xFFE0785A.toInt()), floatArrayOf(0f, 0.45f, 0.75f, 1f))
        glow(c, p, w * 0.68f, h * 0.86f, min(w, h) * 0.55f, 0xFFFFB46B.toInt(), 170)
        p.color = 0x5A1A0F2E
        val band = Path()
        for (k in 0..1) {
            band.reset()
            val base = h * (0.84f + k * 0.07f)
            band.moveTo(0f, h)
            band.lineTo(0f, base)
            var x = 0f
            while (x <= w) {
                band.lineTo(x, base - h * 0.035f * sin((x / w) * PI * (1.4 + k) + k).toFloat())
                x += w / 48f
            }
            band.lineTo(w, h)
            band.close()
            c.drawPath(band, p)
        }
    }

    /** Deep teal water with a light shaft from above and gentle wave bands. */
    private fun ocean(c: Canvas, p: Paint, w: Float, h: Float) {
        vertical(c, p, w, h, intArrayOf(0xFF04496B.toInt(), 0xFF032E48.toInt(), 0xFF021522.toInt()), floatArrayOf(0f, 0.5f, 1f))
        glow(c, p, w * 0.35f, -h * 0.1f, max(w, h) * 0.7f, 0xFF5FD3F3.toInt(), 110)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, h / 360f)
        val wave = Path()
        for (k in 0 until 7) {
            wave.reset()
            val y0 = h * (0.45f + k * 0.08f)
            wave.moveTo(0f, y0)
            var x = 0f
            while (x <= w) {
                wave.lineTo(x, y0 + h * 0.018f * sin((x / w) * PI * 2.6 + k * 0.9).toFloat())
                x += w / 64f
            }
            p.color = (((18 - k * 2).coerceAtLeast(4)) shl 24) or 0x9FE8FF
            c.drawPath(wave, p)
        }
        p.style = Paint.Style.FILL
        vignette(c, p, w, h, 0x70)
    }

    /** Dark graphite with a faint grid and a vignette. */
    private fun graphite(c: Canvas, p: Paint, w: Float, h: Float) {
        vertical(c, p, w, h, intArrayOf(0xFF1A1D23.toInt(), 0xFF101216.toInt()), null)
        val step = h / 15f
        p.strokeWidth = max(1f, h / 1080f)
        p.color = 0x0BFFFFFF
        var x = (w % step) / 2f
        while (x < w) { c.drawLine(x, 0f, x, h, p); x += step }
        var y = (h % step) / 2f
        while (y < h) { c.drawLine(0f, y, w, y, p); y += step }
        glow(c, p, w * 0.5f, h * 0.35f, max(w, h) * 0.5f, 0xFF8A94A8.toInt(), 26)
        vignette(c, p, w, h, 0xB0)
    }

    /** Radial nebula glows plus a deterministic seeded star field. */
    private fun nebula(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        glow(c, p, w * 0.30f, h * 0.40f, s * 0.75f, 0xFF6A2FD6.toInt(), 120)
        glow(c, p, w * 0.70f, h * 0.30f, s * 0.60f, 0xFF2463E8.toInt(), 110)
        glow(c, p, w * 0.58f, h * 0.70f, s * 0.50f, 0xFFD94C9A.toInt(), 80)
        val scale = h / 1080f
        val stars = StarField.generate(NEBULA_SEED, StarField.countFor(w.toInt(), h.toInt()), w.toInt(), h.toInt())
        p.color = 0xFFFFFFFF.toInt()
        var i = 0
        while (i < stars.size) {
            p.alpha = (stars[i + 3] * 255).toInt()
            c.drawCircle(stars[i], stars[i + 1], max(0.5f, stars[i + 2] * scale * 1.4f), p)
            i += StarField.STRIDE
        }
        p.alpha = 255
        vignette(c, p, w, h, 0x90)
    }

    /** Near-black with one faint accent bloom. */
    private fun mono(c: Canvas, p: Paint, w: Float, h: Float, accent: Int) {
        glow(c, p, w * 0.82f, h * 0.88f, max(w, h) * 0.6f, accent, 46)
        vignette(c, p, w, h, 0x60)
    }

    private const val NEBULA_SEED = 0x7E1A5L
}
