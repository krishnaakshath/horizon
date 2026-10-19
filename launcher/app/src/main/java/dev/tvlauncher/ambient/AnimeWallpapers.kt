package dev.tvlauncher.ambient

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The "Anime" built-in collection: original, license-clean procedural art in the mood of popular
 * shonen series (energy auras, cursed-energy swirls, a walled city at dusk, a hidden village at
 * sunset, night blossoms, neon rain, a moonlit sea). No characters, names, symbols or logos.
 * Every scene is seeded, so a render is identical each time; a few hundred shapes at most.
 */
internal object AnimeWallpapers {
    /** Small deterministic generator (LCG), so scenes never change between renders. */
    private class Rnd(seed: Long) {
        private var s = seed xor 0x5DEECE66DL
        fun f(): Float {
            s = (s * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
            return (s ushr 24).toInt().toFloat() / (1 shl 24)
        }
        fun range(a: Float, b: Float) = a + (b - a) * f()
    }

    private fun argb(a: Int, rgb: Int) = (a.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun stars(c: Canvas, p: Paint, w: Float, h: Float, maxY: Float, count: Int, seed: Long) {
        val r = Rnd(seed)
        p.style = Paint.Style.FILL
        val scale = h / 1080f
        repeat(count) {
            p.color = argb((40 + r.f() * 170).toInt(), 0xFFFFFF)
            c.drawCircle(r.f() * w, r.f() * maxY, max(0.5f, (0.5f + r.f() * r.f() * 1.8f) * scale), p)
        }
    }

    // --- Golden Aura: a blazing golden energy aura rising under a dark storm sky ----------------

    fun goldenAura(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        BuiltInWallpapers.vertical(c, p, w, h, intArrayOf(0xFF04050C.toInt(), 0xFF0D0B1C.toInt(), 0xFF1F1408.toInt()), floatArrayOf(0f, 0.55f, 1f))
        BuiltInWallpapers.glow(c, p, w * 0.18f, h * 0.18f, s * 0.6f, 0xFF2B2F5C.toInt(), 90)
        BuiltInWallpapers.glow(c, p, w * 0.85f, h * 0.12f, s * 0.55f, 0xFF262A52.toInt(), 80)
        val cx = w * 0.5f; val cy = h * 0.74f
        BuiltInWallpapers.glow(c, p, cx, cy, s * 0.95f, 0xFFFFB524.toInt(), 120)
        // Flame spikes in a rising fan, outer (amber) then inner (pale gold).
        val r = Rnd(0xA0_2A)
        val flame = Path()
        for (layer in 0..1) {
            val n = if (layer == 0) 34 else 22
            for (i in 0 until n) {
                val t = (i + r.f() * 0.6f) / n
                val ang = (PI * (1.08 + 0.84 * t)).toFloat() // 194..345 deg: up and sideways
                val len = s * (if (layer == 0) r.range(0.35f, 0.72f) else r.range(0.22f, 0.45f)) *
                    (0.65f + 0.35f * sin(PI * t).toFloat())
                val baseW = s * (if (layer == 0) 0.05f else 0.035f)
                val bx = cx + cos(ang) * s * 0.05f
                val by = cy + sin(ang) * s * 0.03f
                val tx = cx + cos(ang) * len
                val ty = cy + sin(ang) * len
                val nx = -sin(ang) * baseW; val ny = cos(ang) * baseW
                val bend = r.range(-0.25f, 0.25f) * len
                flame.reset()
                flame.moveTo(bx + nx, by + ny)
                flame.quadTo((bx + tx) / 2 + nx + bend * -sin(ang), (by + ty) / 2 + ny + bend * cos(ang), tx, ty)
                flame.quadTo((bx + tx) / 2 - nx + bend * -sin(ang), (by + ty) / 2 - ny + bend * cos(ang), bx - nx, by - ny)
                flame.close()
                val col = if (layer == 0) 0xFFFF9E1B.toInt() else 0xFFFFE27A.toInt()
                p.shader = LinearGradient(bx, by, tx, ty, argb(if (layer == 0) 150 else 190, col), argb(0, col), Shader.TileMode.CLAMP)
                c.drawPath(flame, p)
            }
        }
        p.shader = null
        BuiltInWallpapers.glow(c, p, cx, cy, s * 0.22f, 0xFFFFF4C8.toInt(), 230)
        // Rising sparks.
        repeat(170) {
            val a = (PI * r.range(1.05f, 1.95f)).toFloat()
            val d = s * r.range(0.08f, 0.85f)
            p.color = argb((60 + r.f() * 190).toInt(), if (r.f() < 0.7f) 0xFFD86A else 0xFFF8E0)
            c.drawCircle(cx + cos(a) * d * 1.3f, cy + sin(a) * d, max(1f, s * r.range(0.0015f, 0.005f)), p)
        }
        // Two thin lightning bolts.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (k in 0..1) {
            val bolt = Path()
            var x = w * (if (k == 0) 0.2f else 0.8f); var y = h * 0.05f
            bolt.moveTo(x, y)
            while (y < h * 0.55f) {
                x += r.range(-0.04f, 0.04f) * w; y += r.range(0.03f, 0.07f) * h
                bolt.lineTo(x, y)
            }
            p.color = argb(40, 0x9CC8FF); p.strokeWidth = max(3f, h / 140f); c.drawPath(bolt, p)
            p.color = argb(170, 0xE6F2FF); p.strokeWidth = max(1f, h / 700f); c.drawPath(bolt, p)
        }
        p.style = Paint.Style.FILL
        // Rocky ground silhouette.
        val ground = Path()
        ground.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            ground.lineTo(x, h * (0.88f + 0.04f * sin(x / w * 9f).toFloat() + r.range(-0.012f, 0.012f)))
            x += w / 40f
        }
        ground.lineTo(w, h); ground.close()
        p.color = 0xF0050408.toInt()
        c.drawPath(ground, p)
        BuiltInWallpapers.vignette(c, p, w, h, 0x90)
    }

    // --- Cursed Night: dark violet energy swirling around a pale crescent ------------------------

    fun cursedNight(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        BuiltInWallpapers.vertical(c, p, w, h, intArrayOf(0xFF05030B.toInt(), 0xFF0E0620.toInt(), 0xFF05030A.toInt()), floatArrayOf(0f, 0.5f, 1f))
        val cx = w * 0.62f; val cy = h * 0.48f
        BuiltInWallpapers.glow(c, p, cx, cy, s * 0.9f, 0xFF5B1FB8.toInt(), 120)
        BuiltInWallpapers.glow(c, p, cx, cy, s * 0.35f, 0xFF2C0F66.toInt(), 160)
        val r = Rnd(0xC0_5E)
        val colors = intArrayOf(0x9A4DFF, 0x5B3BFF, 0xC02C8E, 0x7E5CFF)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val swirl = Path()
        for (k in 0 until 14) {
            swirl.reset()
            val start = r.f() * 2f * PI.toFloat()
            val r0 = s * r.range(0.05f, 0.18f)
            val grow = s * r.range(0.035f, 0.075f)
            val turns = r.range(1.4f, 2.6f)
            var th = 0f
            var first = true
            while (th < turns * 2f * PI.toFloat()) {
                val rad = r0 + grow * th
                val px = cx + cos(start + th) * rad * 1.35f
                val py = cy + sin(start + th) * rad
                if (first) { swirl.moveTo(px, py); first = false } else swirl.lineTo(px, py)
                th += 0.08f
            }
            val col = colors[k % colors.size]
            p.color = argb(26, col); p.strokeWidth = s * r.range(0.02f, 0.045f); c.drawPath(swirl, p)
            p.color = argb((70 + r.f() * 90).toInt(), col); p.strokeWidth = max(1f, s * r.range(0.002f, 0.006f)); c.drawPath(swirl, p)
        }
        p.style = Paint.Style.FILL
        // Drifting embers of energy.
        repeat(130) {
            p.color = argb((40 + r.f() * 150).toInt(), if (r.f() < 0.5f) 0xB78CFF else 0xFF6FCF)
            c.drawCircle(r.f() * w, r.f() * h, max(0.8f, s * r.range(0.001f, 0.004f)), p)
        }
        // Pale crescent moon, top left.
        val mx = w * 0.2f; val my = h * 0.22f; val mr = s * 0.075f
        BuiltInWallpapers.glow(c, p, mx, my, mr * 3.2f, 0xFFB9A8FF.toInt(), 70)
        val moon = Path().apply { addCircle(mx, my, mr, Path.Direction.CW) }
        val bite = Path().apply { addCircle(mx + mr * 0.45f, my - mr * 0.2f, mr * 0.9f, Path.Direction.CW) }
        moon.op(bite, Path.Op.DIFFERENCE)
        p.color = 0xFFE9E2FF.toInt()
        c.drawPath(moon, p)
        BuiltInWallpapers.vignette(c, p, w, h, 0xC0)
    }

    // --- Beyond the Walls: a colossal wall at dusk, a giant shadow behind it ---------------------

    fun beyondWalls(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        BuiltInWallpapers.vertical(c, p, w, h,
            intArrayOf(0xFF151432.toInt(), 0xFF4A2A52.toInt(), 0xFFC86A4A.toInt(), 0xFFF0B06A.toInt()), floatArrayOf(0f, 0.35f, 0.58f, 0.66f))
        BuiltInWallpapers.glow(c, p, w * 0.42f, h * 0.6f, s * 0.7f, 0xFFFFC27A.toInt(), 150)
        val r = Rnd(0xB3_11)
        // The giant's hazy head and shoulders, looming above the wall line.
        p.color = argb(120, 0x3B2238)
        val gx = w * 0.72f
        val giant = Path().apply {
            addOval(RectF(gx - s * 0.06f, h * 0.33f, gx + s * 0.06f, h * 0.33f + s * 0.16f), Path.Direction.CW)
            moveTo(gx - s * 0.035f, h * 0.33f + s * 0.14f)
            lineTo(gx - s * 0.05f, h * 0.33f + s * 0.2f)
            cubicTo(gx - s * 0.2f, h * 0.33f + s * 0.21f, gx - s * 0.3f, h * 0.33f + s * 0.25f, gx - s * 0.34f, h * 0.7f)
            lineTo(gx + s * 0.34f, h * 0.7f)
            cubicTo(gx + s * 0.3f, h * 0.33f + s * 0.25f, gx + s * 0.2f, h * 0.33f + s * 0.21f, gx + s * 0.05f, h * 0.33f + s * 0.2f)
            lineTo(gx + s * 0.035f, h * 0.33f + s * 0.14f)
            close()
        }
        c.drawPath(giant, p)
        BuiltInWallpapers.glow(c, p, gx, h * 0.45f, s * 0.4f, 0xFFF0B06A.toInt(), 55) // haze in front of it
        // The wall: a gently curved band with stone courses, buttresses and a crenellated top.
        fun top(x: Float): Float { val u = (x - w / 2f) / (w / 2f); return h * (0.55f + 0.035f * u * u) }
        val wall = Path()
        wall.moveTo(0f, h)
        var x = 0f
        while (x <= w) { wall.lineTo(x, top(x)); x += w / 64f }
        wall.lineTo(w, h); wall.close()
        p.shader = LinearGradient(0f, h * 0.55f, 0f, h * 0.8f, 0xFF3A2433.toInt(), 0xFF1C1220.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(wall, p)
        p.shader = null
        p.color = 0x22000000
        p.strokeWidth = max(1f, h / 900f)
        var y = h * 0.6f
        while (y < h * 0.8f) { c.drawLine(0f, y, w, y, p); y += h * 0.022f }
        val step = w / 13f
        x = step / 2f
        while (x < w) {
            p.color = 0x30000000
            c.drawRect(x - w * 0.006f, top(x) - h * 0.005f, x + w * 0.006f, h, p)
            x += step
        }
        p.color = 0xFF2A1A26.toInt()
        x = 0f
        while (x < w) { c.drawRect(x, top(x) - h * 0.012f, x + w * 0.012f, top(x) + 1f, p); x += w * 0.024f }
        // Town rooftops and a bell tower in the foreground.
        val town = Path()
        town.moveTo(0f, h)
        x = 0f
        while (x < w) {
            val bw = w * r.range(0.025f, 0.06f)
            val base = h * r.range(0.76f, 0.82f)
            town.lineTo(x, base); town.lineTo(x + bw / 2f, base - h * r.range(0.025f, 0.05f)); town.lineTo(x + bw, base)
            x += bw
        }
        town.lineTo(w, h); town.close()
        p.color = 0xFF0E0810.toInt()
        c.drawPath(town, p)
        c.drawRect(w * 0.22f, h * 0.62f, w * 0.245f, h * 0.8f, p)
        val spire = Path().apply { moveTo(w * 0.217f, h * 0.62f); lineTo(w * 0.2325f, h * 0.55f); lineTo(w * 0.248f, h * 0.62f); close() }
        c.drawPath(spire, p)
        // Warm windows.
        repeat(26) {
            p.color = argb((120 + r.f() * 100).toInt(), 0xFFC872)
            val wx = r.f() * w; val wy = h * r.range(0.83f, 0.95f)
            c.drawRect(wx, wy, wx + s * 0.006f, wy + s * 0.008f, p)
        }
        // A few birds.
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, h / 600f)
        p.color = 0xAA20141C.toInt()
        repeat(7) {
            val bx = w * r.range(0.3f, 0.6f); val by = h * r.range(0.18f, 0.35f); val bs = s * r.range(0.008f, 0.016f)
            c.drawLine(bx - bs, by - bs * 0.5f, bx, by, p); c.drawLine(bx, by, bx + bs, by - bs * 0.5f, p)
        }
        p.style = Paint.Style.FILL
        BuiltInWallpapers.vignette(c, p, w, h, 0x80)
    }

    // --- Leaf Village Dusk: a hidden village under a swirling sunset, leaves on the wind ---------

    fun leafDusk(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        BuiltInWallpapers.vertical(c, p, w, h,
            intArrayOf(0xFF2A1C4C.toInt(), 0xFF8A3B54.toInt(), 0xFFE0703F.toInt(), 0xFFF7B45E.toInt()), floatArrayOf(0f, 0.4f, 0.62f, 0.75f))
        BuiltInWallpapers.glow(c, p, w * 0.3f, h * 0.62f, s * 0.6f, 0xFFFFD08A.toInt(), 170)
        p.color = 0xFFFFE2A8.toInt()
        c.drawCircle(w * 0.3f, h * 0.62f, s * 0.085f, p)
        val r = Rnd(0x1E_AF)
        // Swirl clouds.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val swirl = Path()
        for (k in 0 until 5) {
            swirl.reset()
            val sx = w * r.range(0.45f, 0.95f); val sy = h * r.range(0.12f, 0.4f)
            var th = 0f
            var first = true
            while (th < 4.2f * PI.toFloat()) {
                val rad = s * 0.006f * th * th
                val px = sx + cos(th) * rad * 1.6f; val py = sy + sin(th) * rad
                if (first) { swirl.moveTo(px, py); first = false } else swirl.lineTo(px, py)
                th += 0.1f
            }
            p.color = argb(60, 0xFFD9B0); p.strokeWidth = max(2f, s * 0.006f)
            c.drawPath(swirl, p)
        }
        p.style = Paint.Style.FILL
        // Two hill layers.
        for (layer in 0..1) {
            val hill = Path()
            hill.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                val base = if (layer == 0) 0.66f else 0.74f
                hill.lineTo(x, h * (base + 0.05f * sin(x / w * (3.2f + layer) + layer * 2f).toFloat()))
                x += w / 48f
            }
            hill.lineTo(w, h); hill.close()
            p.color = if (layer == 0) 0xFF4A2340.toInt() else 0xFF2A1426.toInt()
            c.drawPath(hill, p)
        }
        // Pagoda-style rooftops with upturned eaves, and warm lanterns.
        p.color = 0xFF140A12.toInt()
        var x = w * 0.02f
        while (x < w) {
            val bw = s * r.range(0.09f, 0.16f)
            val base = h * r.range(0.8f, 0.86f)
            val tiers = if (r.f() < 0.3f) 2 else 1
            c.drawRect(x + bw * 0.15f, base, x + bw * 0.85f, h, p)
            for (t in 0 until tiers) {
                val ty = base - t * bw * 0.32f
                val roof = Path().apply {
                    moveTo(x - bw * 0.06f, ty - bw * 0.04f)
                    quadTo(x + bw * 0.15f, ty - bw * 0.06f, x + bw * 0.3f, ty - bw * 0.26f)
                    lineTo(x + bw * 0.7f, ty - bw * 0.26f)
                    quadTo(x + bw * 0.85f, ty - bw * 0.06f, x + bw * 1.06f, ty - bw * 0.04f)
                    lineTo(x + bw * 0.85f, ty + bw * 0.02f); lineTo(x + bw * 0.15f, ty + bw * 0.02f); close()
                }
                c.drawPath(roof, p)
                if (t + 1 < tiers) c.drawRect(x + bw * 0.25f, ty - bw * 0.34f, x + bw * 0.75f, ty - bw * 0.2f, p)
            }
            x += bw * r.range(1.05f, 1.6f)
        }
        repeat(18) {
            val lx = r.f() * w; val ly = h * r.range(0.86f, 0.95f)
            BuiltInWallpapers.glow(c, p, lx, ly, s * 0.02f, 0xFFFFB050.toInt(), 120)
            p.color = 0xFFFFC874.toInt()
            c.drawCircle(lx, ly, max(1f, s * 0.004f), p)
        }
        // Leaves on the wind.
        val leaf = RectF()
        repeat(70) {
            val lx = r.f() * w; val ly = r.f() * h * 0.9f; val ls = s * r.range(0.008f, 0.02f)
            p.color = argb((140 + r.f() * 100).toInt(), intArrayOf(0x6FBF4E, 0x9BCB4A, 0xF0A040, 0xE0623A)[(r.f() * 4).toInt().coerceAtMost(3)])
            c.save()
            c.rotate(r.f() * 360f, lx, ly)
            leaf.set(lx - ls, ly - ls * 0.45f, lx + ls, ly + ls * 0.45f)
            c.drawOval(leaf, p)
            c.restore()
        }
        BuiltInWallpapers.vignette(c, p, w, h, 0x70)
    }

    // --- Petal Night: a blossom branch against a full moon --------------------------------------

    fun petalNight(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        BuiltInWallpapers.vertical(c, p, w, h, intArrayOf(0xFF070B22.toInt(), 0xFF16173F.toInt(), 0xFF2C1C4A.toInt()), floatArrayOf(0f, 0.6f, 1f))
        stars(c, p, w, h, h * 0.8f, 140, 0x9E7L)
        val mx = w * 0.74f; val my = h * 0.32f
        BuiltInWallpapers.glow(c, p, mx, my, s * 0.55f, 0xFFB8C4FF.toInt(), 90)
        p.color = 0xFFF4EFDF.toInt()
        c.drawCircle(mx, my, s * 0.15f, p)
        p.color = 0x14000000
        c.drawCircle(mx - s * 0.04f, my + s * 0.03f, s * 0.035f, p)
        c.drawCircle(mx + s * 0.05f, my - s * 0.05f, s * 0.022f, p)
        val r = Rnd(0x5A_C0)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF0A0710.toInt()
        val tips = ArrayList<FloatArray>()
        fun branch(x: Float, y: Float, ang: Float, len: Float, width: Float, depth: Int) {
            val ex = x + cos(ang) * len; val ey = y + sin(ang) * len
            p.strokeWidth = max(1f, width)
            val path = Path().apply { moveTo(x, y); quadTo((x + ex) / 2 + r.range(-0.15f, 0.15f) * len, (y + ey) / 2 + r.range(-0.15f, 0.15f) * len, ex, ey) }
            c.drawPath(path, p)
            if (depth == 0) { tips += floatArrayOf(ex, ey); return }
            branch(ex, ey, ang + r.range(-0.6f, -0.15f), len * r.range(0.6f, 0.8f), width * 0.62f, depth - 1)
            branch(ex, ey, ang + r.range(0.15f, 0.6f), len * r.range(0.6f, 0.8f), width * 0.62f, depth - 1)
            if (r.f() < 0.4f) tips += floatArrayOf(ex, ey)
        }
        branch(-w * 0.02f, h * 0.12f, 0.35f, s * 0.38f, s * 0.035f, 4)
        p.style = Paint.Style.FILL
        for (t in tips) {
            repeat(9) {
                p.color = argb((150 + r.f() * 100).toInt(), if (r.f() < 0.6f) 0xF6A9C6 else 0xFFD9E6)
                c.drawCircle(t[0] + r.range(-1f, 1f) * s * 0.03f, t[1] + r.range(-1f, 1f) * s * 0.025f, s * r.range(0.004f, 0.009f), p)
            }
        }
        val petal = RectF()
        repeat(80) {
            val px = r.f() * w; val py = r.f() * h; val ps = s * r.range(0.005f, 0.011f)
            p.color = argb((110 + r.f() * 120).toInt(), 0xF7B3CB)
            c.save(); c.rotate(r.f() * 360f, px, py)
            petal.set(px - ps, py - ps * 0.55f, px + ps, py + ps * 0.55f)
            c.drawOval(petal, p); c.restore()
        }
        BuiltInWallpapers.vignette(c, p, w, h, 0x80)
    }

    // --- Neon Rain: a night city skyline in the rain -------------------------------------------

    fun neonRain(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        BuiltInWallpapers.vertical(c, p, w, h, intArrayOf(0xFF05060E.toInt(), 0xFF120A26.toInt(), 0xFF2A0E33.toInt()), floatArrayOf(0f, 0.6f, 1f))
        BuiltInWallpapers.glow(c, p, w * 0.3f, h * 0.75f, s * 0.8f, 0xFFE0307A.toInt(), 90)
        BuiltInWallpapers.glow(c, p, w * 0.8f, h * 0.7f, s * 0.7f, 0xFF1FB8E0.toInt(), 70)
        val r = Rnd(0x4E_0A)
        val layers = intArrayOf(0xFF1C1634.toInt(), 0xFF100C20.toInt(), 0xFF050409.toInt())
        for ((li, col) in layers.withIndex()) {
            var x = -w * 0.02f
            while (x < w) {
                val bw = w * r.range(0.03f, 0.08f)
                val top = h * (r.range(0.25f, 0.55f) + li * 0.1f)
                p.color = col
                c.drawRect(x, top, x + bw, h, p)
                if (li > 0) {
                    var wy = top + s * 0.015f
                    while (wy < h * 0.95f) {
                        var wx = x + bw * 0.12f
                        while (wx < x + bw * 0.85f) {
                            if (r.f() < 0.22f) {
                                p.color = argb((70 + r.f() * 150).toInt(), intArrayOf(0xFF4FA0, 0x4FE3FF, 0xFFC46A, 0xB98CFF)[(r.f() * 4).toInt().coerceAtMost(3)])
                                c.drawRect(wx, wy, wx + s * 0.007f, wy + s * 0.005f, p)
                            }
                            wx += s * 0.014f
                        }
                        wy += s * 0.016f
                    }
                }
                x += bw + w * r.range(0.002f, 0.01f)
            }
        }
        // Neon strips on two towers.
        p.color = argb(200, 0xFF3D8E); c.drawRect(w * 0.18f, h * 0.48f, w * 0.181f + s * 0.004f, h * 0.7f, p)
        p.color = argb(200, 0x3DE0FF); c.drawRect(w * 0.71f, h * 0.42f, w * 0.71f + s * 0.004f, h * 0.66f, p)
        // Rain.
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, h / 1080f)
        repeat(320) {
            val rx = r.f() * w * 1.1f; val ry = r.f() * h; val len = h * r.range(0.02f, 0.06f)
            p.color = argb((20 + r.f() * 45).toInt(), 0xCFE0FF)
            c.drawLine(rx, ry, rx - len * 0.2f, ry + len, p)
        }
        p.style = Paint.Style.FILL
        BuiltInWallpapers.vignette(c, p, w, h, 0x90)
    }

    // --- Moonlit Sea: a quiet sea, a low moon and its long reflection ---------------------------

    fun moonlitSea(c: Canvas, p: Paint, w: Float, h: Float) {
        val s = min(w, h)
        val horizon = h * 0.6f
        BuiltInWallpapers.vertical(c, p, w, h, intArrayOf(0xFF040A1C.toInt(), 0xFF0F2148.toInt(), 0xFF071331.toInt(), 0xFF020611.toInt()), floatArrayOf(0f, 0.6f, 0.61f, 1f))
        stars(c, p, w, h, horizon * 0.9f, 160, 0x5EAL)
        val mx = w * 0.5f; val my = h * 0.3f
        BuiltInWallpapers.glow(c, p, mx, my, s * 0.6f, 0xFF9FB8FF.toInt(), 90)
        p.color = 0xFFF2F0E6.toInt()
        c.drawCircle(mx, my, s * 0.1f, p)
        // Island silhouette with a lone tree.
        p.color = 0xFF03060F.toInt()
        c.drawOval(RectF(w * 0.06f, horizon - h * 0.03f, w * 0.3f, horizon + h * 0.02f), p)
        c.drawRect(w * 0.17f, horizon - h * 0.09f, w * 0.174f, horizon, p)
        c.drawOval(RectF(w * 0.145f, horizon - h * 0.13f, w * 0.2f, horizon - h * 0.075f), p)
        // Reflection column and wave glints.
        val r = Rnd(0x3E_A5)
        var y = horizon + h * 0.01f
        while (y < h) {
            val t = (y - horizon) / (h - horizon)
            val half = s * (0.04f + 0.12f * t) * r.range(0.4f, 1f)
            p.color = argb(((1f - t) * 170 + 30).toInt(), 0xE8ECFF)
            c.drawRect(mx - half + r.range(-1f, 1f) * s * 0.02f, y, mx + half, y + max(1f, h * 0.003f), p)
            y += h * r.range(0.008f, 0.016f)
        }
        repeat(90) {
            val gx = r.f() * w; val gy = r.range(horizon, h)
            p.color = argb((15 + r.f() * 40).toInt(), 0x9FB8FF)
            c.drawRect(gx, gy, gx + s * r.range(0.01f, 0.04f), gy + max(1f, h / 1080f), p)
        }
        BuiltInWallpapers.vignette(c, p, w, h, 0x90)
    }
}
