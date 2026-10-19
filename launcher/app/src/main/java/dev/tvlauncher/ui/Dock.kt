package dev.tvlauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.DockItems.Item
import dev.tvlauncher.ui.Theme.style

/**
 * Floating rounded bar of circular icon buttons (see [DockItems]: Inputs, Settings). The label of
 * the focused button glides in underneath it. DPAD_UP is reported via [onUp].
 */
class Dock(
    context: Context,
    translucent: Boolean,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    onSettingsLongPress: () -> Unit,
    private val onUp: () -> Boolean
) : LinearLayout(context) {
    private val buttons = DockItems.items.map { item ->
        when (item) {
            Item.INPUTS -> DockButton(context, R.drawable.ic_input, "Inputs", onInputs)
            Item.SETTINGS -> DockButton(context, R.drawable.ic_settings, "Settings", onSettings).apply {
                setOnLongClickListener { onSettingsLongPress(); true }
            }
        }
    }
    private val bar = LinearLayout(context).apply {
        orientation = HORIZONTAL
        val p = context.dp(BAR_PAD_DP)
        setPadding(p, p, p, p)
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable().apply {
            setColor(if (translucent) 0x8C151922.toInt() else 0xE6151922.toInt())
            cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat()
            setStroke(context.dp(1), Theme.STROKE)
        }
    }
    private val label = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.medium)
        gravity = Gravity.CENTER
        alpha = 0f
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        buttons.forEachIndexed { i, b ->
            bar.addView(b, LayoutParams(context.dp(BUTTON_DP), context.dp(BUTTON_DP)).apply {
                if (i > 0) marginStart = context.dp(SPACING_DP)
            })
            b.setOnKeyListener { _, code, ev ->
                code == KeyEvent.KEYCODE_DPAD_UP && ev.action == KeyEvent.ACTION_DOWN && onUp()
            }
            b.setOnFocusChangeListener { v, has -> if (has) showLabel(b) else v.post { if (!hasFocus()) hideLabel() } }
        }
        addView(bar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        val labelRow = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        labelRow.addView(label, FrameLayout.LayoutParams(context.dp(LABEL_W_DP), LayoutParams.WRAP_CONTENT))
        addView(labelRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(Theme.S2)
        })
    }

    private fun showLabel(b: DockButton) {
        label.animate().cancel()
        label.text = b.title
        val x = bar.left + b.left + b.width / 2f - label.width / 2f
        if (label.alpha < 0.05f) {
            label.translationX = x
            label.translationY = context.dp(4).toFloat()
        }
        label.animate().alpha(1f).translationX(x).translationY(0f).setDuration(TileSpec.FOCUS_MS).start()
    }

    private fun hideLabel() {
        label.animate().cancel()
        label.animate().alpha(0f).setDuration(TileSpec.FOCUS_MS).start()
    }

    fun focusFirst(): Boolean = buttons[DockItems.firstFocusIndex].requestFocus()

    class DockButton(context: Context, iconRes: Int, val title: String, onClick: () -> Unit) : ImageView(context) {
        private val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL }

        init {
            isFocusable = true
            isClickable = true
            contentDescription = title
            setImageResource(iconRes)
            scaleType = ScaleType.FIT_CENTER
            val p = context.dp(16)
            setPadding(p, p, p, p)
            background = fill
            setOnClickListener { onClick() }
            applyFocus(false)
        }

        private fun applyFocus(f: Boolean) {
            fill.setColor(if (f) Theme.TEXT else 0x14FFFFFF)
            imageTintList = ColorStateList.valueOf(if (f) Theme.TEXT_DARK else Theme.TEXT)
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            applyFocus(gainFocus)
            val s = if (gainFocus) 1.1f else 1f
            animate().scaleX(s).scaleY(s).setDuration(TileSpec.FOCUS_MS).start()
        }
    }

    private companion object {
        const val BUTTON_DP = 56
        const val SPACING_DP = 16
        const val BAR_PAD_DP = 8
        const val LABEL_W_DP = 120
    }
}
