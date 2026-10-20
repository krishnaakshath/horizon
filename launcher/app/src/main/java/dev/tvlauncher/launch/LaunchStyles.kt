package dev.tvlauncher.launch

import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.splash.LaunchStyle
import dev.tvlauncher.splash.MotionCaps
import dev.tvlauncher.splash.MotionPace

/**
 * The chosen launch style on top of [LaunchMotion]: the same per-app timing (slow starters longer,
 * warm apps shorter), scaled by the pace and always kept within [MotionCaps.launch] for the level
 * (LITE never past 350 ms). Style Off, animation level Off and a system animator scale of 0 all give
 * [LaunchMotion.NONE] (the plain loader only). Pure.
 */
object LaunchStyles {
    const val MIN_MS = 150L

    data class Styled(val style: LaunchStyle, val plan: LaunchMotion.Plan) {
        val animated: Boolean get() = plan.animated
    }

    fun plan(style: LaunchStyle, motion: EffectsMode?, animatorScale: Float, pkg: String, warm: Boolean, pace: MotionPace): Styled {
        if (style == LaunchStyle.OFF) return Styled(style, LaunchMotion.NONE)
        val base = LaunchMotion.plan(motion, animatorScale, pkg, warm)
        if (!base.animated) return Styled(style, base)
        val cap = MotionCaps.launch(motion == EffectsMode.FULL)
        val ms = (base.expandMs * pace.factor).toLong().coerceIn(MIN_MS, cap)
        val logoDelay = ms * 2 / 5
        return Styled(style, base.copy(expandMs = ms, logoDelayMs = logoDelay, logoMs = ms - logoDelay))
    }
}

/**
 * Geometry of the launch card per style at progress [t] (0..1), written into [out] as
 * left, top, right, bottom, alpha (0..1), corner factor (0..1). [from] is the tile's
 * left, top, right, bottom in overlay coordinates, or null for "from the centre". Pure and
 * allocation-free (the caller owns [out]).
 */
object LaunchGeometry {
    const val START_ALPHA = 0xD0 / 255f
    const val OUT_SIZE = 6

    fun frame(style: LaunchStyle, t: Float, from: FloatArray?, w: Float, h: Float, out: FloatArray) {
        val p = t.coerceIn(0f, 1f)
        val sl: Float; val st: Float; val sr: Float; val sb: Float
        if (from == null) { sl = w * 0.4f; st = h * 0.4f; sr = w * 0.6f; sb = h * 0.6f }
        else { sl = from[0]; st = from[1]; sr = from[2]; sb = from[3] }
        when (style) {
            LaunchStyle.FADE -> {
                out[0] = 0f; out[1] = 0f; out[2] = w; out[3] = h
                out[4] = p; out[5] = 0f
            }
            LaunchStyle.ZOOM -> {
                // The tile keeps its shape and grows about a centre that glides to the screen's centre.
                val tw = (sr - sl).coerceAtLeast(1f); val th = (sb - st).coerceAtLeast(1f)
                val scale = 1f + (maxOf(w / tw, h / th) - 1f) * p
                val cx = (sl + sr) / 2f + (w / 2f - (sl + sr) / 2f) * p
                val cy = (st + sb) / 2f + (h / 2f - (st + sb) / 2f) * p
                out[0] = cx - tw * scale / 2f; out[1] = cy - th * scale / 2f
                out[2] = cx + tw * scale / 2f; out[3] = cy + th * scale / 2f
                out[4] = 1f; out[5] = 1f - p
            }
            else -> { // BRAND_EXPAND (and OFF, which never animates)
                out[0] = sl * (1 - p); out[1] = st * (1 - p)
                out[2] = sr + (w - sr) * p; out[3] = sb + (h - sb) * p
                out[4] = START_ALPHA + (1f - START_ALPHA) * p; out[5] = 1f - p
            }
        }
    }
}
