package dev.tvlauncher.room

import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2.x relative luminance / contrast ratio and simple sRGB alpha compositing. Pure. */
object Contrast {
    /** sRGB channel (0..255) to linear light (0..1). */
    fun toLinear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** Linear light (0..1) to an sRGB channel (0..255), rounded and clamped. */
    fun toSrgb(l: Double): Int {
        val x = if (l.isNaN()) 0.0 else l.coerceIn(0.0, 1.0)
        val s = if (x <= 0.0031308) x * 12.92 else 1.055 * x.pow(1.0 / 2.4) - 0.055
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }

    /** Relative luminance of the RGB part of [argb] (alpha ignored), 0..1. */
    fun luminance(argb: Int): Double =
        0.2126 * toLinear((argb shr 16) and 255) + 0.7152 * toLinear((argb shr 8) and 255) + 0.0722 * toLinear(argb and 255)

    /** WCAG contrast ratio, 1..21, symmetric. Both colours are treated as opaque. */
    fun ratio(fg: Int, bg: Int): Double {
        val a = luminance(fg); val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** [fg] (with alpha) drawn over [bg] the way Android's canvas does it (sRGB source-over). */
    fun over(fg: Int, bg: Int): Int {
        val af = ((fg ushr 24) and 255) / 255.0
        val ab = ((bg ushr 24) and 255) / 255.0
        val ao = af + ab * (1 - af)
        if (ao <= 0.0) return 0
        fun ch(s: Int): Int {
            val f = (fg shr s) and 255; val b = (bg shr s) and 255
            return ((f * af + b * ab * (1 - af)) / ao).roundToInt().coerceIn(0, 255)
        }
        val a = (ao * 255).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
