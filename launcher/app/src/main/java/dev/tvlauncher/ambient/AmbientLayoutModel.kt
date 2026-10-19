package dev.tvlauncher.ambient

import kotlin.math.max
import kotlin.math.min

/** Pixel positions/sizes for [AmbientView]. Text sizes are in sp, everything else in px. */
data class AmbientLayout(
    val widthPx: Int,
    val heightPx: Int,
    /** Content box: overscan-safe margins plus the pixel-shift slack on every side. */
    val contentLeft: Int,
    val contentTop: Int,
    val contentRight: Int,
    val contentBottom: Int,
    val shiftMaxPx: Int,
    val clockSp: Float,
    val dateSp: Float,
    val tempSp: Float,
    val conditionSp: Float,
    val metaSp: Float,
    val chipSp: Float,
    val weatherIconPx: Int,
    val forecastIconPx: Int,
    val netIconPx: Int,
    val gapPx: Int,
    val showForecast: Boolean,
    val maxChips: Int,
    val chipMaxWidthPx: Int,
    val weatherBlockWidthPx: Int
)

object AmbientLayoutModel {
    const val SAFE_H_DP = 48
    const val SAFE_V_DP = 27
    const val SHIFT_DP = 12
    const val MIN_CLOCK_SP = 96f
    const val MAX_CLOCK_SP = 140f
    private const val CLOCK_HEIGHT_RATIO = 0.24f
    private const val FORECAST_MIN_W_DP = 800f
    private const val FORECAST_MIN_H_DP = 480f

    /** Layout for a [widthPx]x[heightPx] screen; portrait-reported metrics are treated as landscape. */
    fun compute(widthPx: Int, heightPx: Int, density: Float, lite: Boolean = false): AmbientLayout {
        val d = if (density > 0f) density else 1f
        var w = max(widthPx, heightPx)
        var h = min(widthPx, heightPx)
        if (w <= 0 || h <= 0) { w = (960 * d).toInt(); h = (540 * d).toInt() }
        val wDp = w / d
        val hDp = h / d
        fun px(dp: Float): Int = (dp * d + 0.5f).toInt()
        val shift = px(SHIFT_DP.toFloat())
        val left = px(SAFE_H_DP.toFloat()) + shift
        val top = px(SAFE_V_DP.toFloat()) + shift
        val clock = (hDp * CLOCK_HEIGHT_RATIO).coerceIn(MIN_CLOCK_SP, MAX_CLOCK_SP)
        return AmbientLayout(
            widthPx = w,
            heightPx = h,
            contentLeft = left,
            contentTop = top,
            contentRight = w - left,
            contentBottom = h - top,
            shiftMaxPx = shift,
            clockSp = clock,
            dateSp = 22f,
            tempSp = (clock * 0.42f).coerceAtLeast(40f),
            conditionSp = 18f,
            metaSp = 14f,
            chipSp = 14f,
            weatherIconPx = px(56f),
            forecastIconPx = px(28f),
            netIconPx = px(20f),
            gapPx = px(12f),
            showForecast = !lite && wDp >= FORECAST_MIN_W_DP && hDp >= FORECAST_MIN_H_DP,
            maxChips = 3,
            chipMaxWidthPx = px(min(360f, wDp * 0.3f)),
            weatherBlockWidthPx = px(min(420f, wDp * 0.4f))
        )
    }
}
