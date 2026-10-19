package dev.tvlauncher.browser.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.browser.R
import dev.tvlauncher.browser.cursor.CursorRenderer
import dev.tvlauncher.browser.cursor.CursorSettings
import dev.tvlauncher.browser.cursor.CursorStyle
import dev.tvlauncher.browser.cursor.Magnet
import dev.tvlauncher.browser.ui.Theme.style
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** "Cursor" settings page: live preview + D-pad rows (LEFT/RIGHT change, OK cycles). */
class CursorSettingsPage(
    context: Context,
    private var settings: CursorSettings,
    private val onChange: (CursorSettings) -> Unit
) : FrameLayout(context) {
    private val preview = Preview(context)
    private val rows = MenuCard(context)
    var onClose: (() -> Unit)? = null

    init {
        setBackgroundColor(0xCC000000.toInt())
        isClickable = true
        visibility = GONE
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Theme.rounded(context, 0xFA141821.toInt(), 26, Theme.STROKE, 1)
            setPadding(context.dp(22), context.dp(22), context.dp(22), context.dp(22))
            elevation = context.dpf(20f)
        }
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(context).apply { style(24f, Theme.medium); text = "Cursor" })
        left.addView(TextView(context).apply { style(13f, Theme.regular, Theme.TEXT_2); text = "Changes apply instantly." },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(4) })
        left.addView(preview, LinearLayout.LayoutParams(context.dp(260), context.dp(300)).apply { topMargin = context.dp(16) })
        box.addView(left)
        rows.background = null
        rows.elevation = 0f
        box.addView(rows, LinearLayout.LayoutParams(-2, context.dp(370)).apply { marginStart = context.dp(18) })
        addView(box, LayoutParams(-2, -2, Gravity.CENTER))
        rows.onClose = { close() }
    }

    val isOpen get() = visibility == VISIBLE

    fun open(current: CursorSettings) {
        settings = current
        preview.renderer.apply(settings)
        visibility = VISIBLE
        rows.open(null, items(), widthDp = 340)
        preview.start()
    }

    fun close() {
        if (!isOpen) return
        visibility = GONE
        preview.stop()
        onClose?.invoke()
    }

    private fun <T> cycle(list: List<T>, cur: T, d: Int): T {
        val i = list.indexOf(cur).let { if (it < 0) 0 else it }
        return list[Math.floorMod(i + d, list.size)]
    }

    private fun set(s: CursorSettings) {
        settings = s
        preview.renderer.apply(s)
        preview.invalidate()
        onChange(s)
    }

    private fun items(): List<MenuCard.Item> {
        fun item(icon: Int, label: String, value: () -> String, adj: (Int) -> Unit) =
            MenuCard.Item(icon, label, value, adjust = adj, keepOpen = true) { adj(1) }
        return listOf(
            item(R.drawable.ic_cursor, "Style", { settings.style.label }) { d -> set(settings.copy(style = cycle(CursorStyle.values().toList(), settings.style, d))) },
            item(R.drawable.ic_zoom, "Size", { sizeLabel(settings.sizeDp) }) { d -> set(settings.copy(sizeDp = cycle(CursorSettings.SIZES, settings.sizeDp, d))) },
            item(R.drawable.ic_star, "Color", { colorName(settings.color) }) { d ->
                val list = CursorSettings.COLORS.toList()
                set(settings.copy(color = cycle(list, settings.color, d)))
            },
            item(R.drawable.ic_find, "Opacity", { "${(settings.opacity * 100).toInt()}%" }) { d -> set(settings.copy(opacity = cycle(CursorSettings.OPACITIES, settings.opacity, d))) },
            item(R.drawable.ic_forward, "Speed", { "${settings.speed}x" }) { d -> set(settings.copy(speed = cycle(CursorSettings.SPEEDS, settings.speed, d))) },
            item(R.drawable.ic_plus, "Magnetic links", { settings.magnet.label }) { d -> set(settings.copy(magnet = cycle(Magnet.values().toList(), settings.magnet, d))) },
            item(R.drawable.ic_history, "Trail", { if (settings.trail) "Short" else "Off" }) { _ -> set(settings.copy(trail = !settings.trail)) },
            item(R.drawable.ic_close, "Auto-hide", { if (settings.autoHideMs == 0L) "Never" else "${settings.autoHideMs / 1000} s" }) { d ->
                set(settings.copy(autoHideMs = cycle(CursorSettings.AUTO_HIDE, settings.autoHideMs, d)))
            },
            MenuCard.Item(R.drawable.ic_reload, "Reset cursor", keepOpen = true) { set(CursorSettings()); rows.open(null, items(), 340) }
        )
    }

    private fun sizeLabel(dp: Int) = when (dp) { 20 -> "S"; 28 -> "M"; 36 -> "L"; else -> "XL" }

    private fun colorName(c: Int) = when (CursorSettings.COLORS.indexOf(c)) {
        0 -> "White"; 1 -> "Blue"; 2 -> "Cyan"; 3 -> "Mint"; 4 -> "Yellow"; 5 -> "Orange"; 6 -> "Pink"; 7 -> "Violet"; 8 -> "Red"; else -> "Custom"
    }

    /** Mini canvas: the chosen pointer gliding in a loop, briefly morphing over a "link" and a "field". */
    private class Preview(context: Context) : View(context) {
        val renderer = CursorRenderer(resources.displayMetrics.density)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF232938.toInt() }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8AB4FF.toInt(); textSize = 15f * resources.displayMetrics.density }
        private val gray = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4152.toInt() }
        private val field = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B7385.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
        private var t = 0f
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
        }
        private val d = resources.displayMetrics.density

        fun start() { if (!anim.isStarted) anim.start() }
        fun stop() = anim.cancel()
        override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            c.drawRoundRect(0f, 0f, w, h, 18 * d, 18 * d, bg)
            c.drawRoundRect(20 * d, 24 * d, w - 20 * d, 34 * d, 5 * d, 5 * d, gray)
            c.drawText("Example link", 20 * d, 80 * d, ink)
            c.drawRoundRect(20 * d, h - 90 * d, w - 20 * d, h - 54 * d, 8 * d, 8 * d, field)
            val a = (t * 2 * PI).toFloat()
            val x = w / 2 + cos(a) * (w * 0.32f)
            val y = h / 2 + sin(2 * a) * (h * 0.30f)
            val overLink = y in 60 * d..90 * d && x in 20 * d..140 * d
            val overField = y in h - 90 * d..h - 54 * d
            renderer.linkAmt += ((if (overLink) 1f else 0f) - renderer.linkAmt) * 0.25f
            renderer.textAmt += ((if (overField) 1f else 0f) - renderer.textAmt) * 0.25f
            renderer.draw(c, x, y, 1f)
        }
    }
}
