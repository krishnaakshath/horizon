package dev.tvlauncher.ui

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout

/** Bottom dock: Settings, Inputs, All Apps, Assistant. DPAD_UP is reported via [onUp]. */
class Dock(
    context: Context,
    onSettings: () -> Unit,
    onInputs: () -> Unit,
    onAllApps: () -> Unit,
    onAssistant: () -> Unit,
    private val onUp: () -> Boolean
) : LinearLayout(context) {
    private val buttons = listOf(
        FocusButton(context, "Settings", onSettings),
        FocusButton(context, "Inputs", onInputs),
        FocusButton(context, "All Apps", onAllApps),
        FocusButton(context, "Assistant", onAssistant)
    )

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        for (b in buttons) {
            addView(b, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(8); marginEnd = context.dp(8)
            })
            b.setOnKeyListener { _, code, ev ->
                code == KeyEvent.KEYCODE_DPAD_UP && ev.action == KeyEvent.ACTION_DOWN && onUp()
            }
        }
    }

    fun focusFirst(): Boolean = buttons.first().requestFocus()
}
