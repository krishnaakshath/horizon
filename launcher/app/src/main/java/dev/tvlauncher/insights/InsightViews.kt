package dev.tvlauncher.insights

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/** Horizontal bar: faint track plus a filled share in the app's brand tint. */
class BarView(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var tint = Theme.TEXT_2
        set(v) { field = v; fill.color = v; invalidate() }

    init { fill.color = tint }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, r, r, track)
        if (fraction > 0f) {
            rect.set(0f, 0f, maxOf(height.toFloat(), width * fraction), height.toFloat())
            c.drawRoundRect(rect, r, r, fill)
        }
    }
}

/** Seven columns, oldest first; the last (today) is highlighted. */
class WeekChartView(context: Context) : View(context) {
    private val col = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F7FA.toInt() }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Theme.medium
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }
    private val value = Paint(label)
    private val rect = RectF()
    private var totals: List<Long> = emptyList()
    private var labels: List<String> = emptyList()
    private var valueText: List<String> = emptyList()

    fun setData(totalsMs: List<Long>, dayLabels: List<String>) {
        totals = totalsMs; labels = dayLabels
        valueText = totalsMs.map { if (it > 0) SummaryText.formatDuration(it) else "" }
        contentDescription = "Viewing time, last ${totalsMs.size} days: " +
            totalsMs.indices.joinToString(", ") { "${dayLabels.getOrElse(it) { "" }} ${SummaryText.formatDuration(totalsMs[it])}" }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val n = totals.size
        if (n == 0) return
        val labelH = label.textSize * 1.6f
        val valueH = value.textSize * 1.6f
        val chartH = height - labelH - valueH
        val max = (totals.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slot = width / n.toFloat()
        val barW = slot * 0.46f
        val radius = context.dp(6).toFloat()
        for (i in 0 until n) {
            val today = i == n - 1
            val cx = slot * i + slot / 2
            val h = maxOf(context.dp(4).toFloat(), chartH * totals[i] / max)
            val bottom = valueH + chartH
            rect.set(cx - barW / 2, bottom - h, cx + barW / 2, bottom)
            c.drawRoundRect(rect, radius, radius, if (today) hi else col)
            label.color = if (today) Theme.TEXT else Theme.TEXT_2
            c.drawText(labels.getOrElse(i) { "" }, cx, height - label.textSize * 0.35f, label)
            if (valueText[i].isNotEmpty()) {
                value.color = if (today) Theme.TEXT else Theme.TEXT_2
                c.drawText(valueText[i], cx, bottom - h - value.textSize * 0.4f, value)
            }
        }
    }
}
