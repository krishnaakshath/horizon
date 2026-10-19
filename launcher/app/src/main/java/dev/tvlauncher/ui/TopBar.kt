package dev.tvlauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.Theme.style

/** Clock + date on the left; Bluetooth, network and an Inputs pill on the right. */
class TopBar(context: Context, onInputs: () -> Unit) : LinearLayout(context) {
    private val clock = TextClock(context).apply {
        format12Hour = "h:mm"
        format24Hour = "HH:mm"
        style(Theme.SP_CLOCK, Theme.light)
    }
    private val date = TextClock(context).apply {
        format12Hour = "EEE, d MMM"
        format24Hour = "EEE, d MMM"
        style(Theme.SP_DATE, Theme.regular, Theme.TEXT_2)
    }
    private val bluetooth = icon(R.drawable.ic_bluetooth)
    private val network = icon(R.drawable.ic_wifi_3)
    val inputsButton = PillButton(context, R.drawable.ic_input, "Inputs", onInputs)
    private val status = StatusIcons(context) { net, bt ->
        network.setImageResource(StatusIcons.drawable(net))
        network.contentDescription = net.name
        bluetooth.setImageResource(StatusIcons.drawable(bt))
        bluetooth.contentDescription = bt.name
        bluetooth.visibility = if (bt == StatusModel.BtIcon.HIDDEN) View.GONE else View.VISIBLE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(View(context).apply {
            background = GradientDrawable().apply { setColor(Theme.TEXT_2); shape = GradientDrawable.OVAL }
            alpha = 0.6f
        }, LayoutParams(context.dp(4), context.dp(4)).apply {
            marginStart = context.dp(Theme.S3); marginEnd = context.dp(Theme.S3)
        })
        addView(date, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(bluetooth, iconParams())
        addView(network, iconParams())
        addView(inputsButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(Theme.S3)
        })
        bluetooth.visibility = View.GONE
    }

    private fun icon(res: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(Theme.TEXT)
        alpha = 0.92f
    }

    private fun iconParams() = LayoutParams(context.dp(22), context.dp(22)).apply {
        marginStart = context.dp(Theme.S4)
    }

    fun startStatus() = status.start()

    fun stopStatus() = status.stop()

    /** Focusable icon + label pill with a white focused state. */
    class PillButton(context: Context, iconRes: Int, text: String, onClick: () -> Unit) : LinearLayout(context) {
        private val iconView = ImageView(context).apply { setImageResource(iconRes) }
        private val label = TextView(context).apply { this.text = text; style(Theme.SP_BUTTON, Theme.medium) }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(context.dp(14), context.dp(8), context.dp(18), context.dp(8))
            addView(iconView, LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(8) })
            addView(label)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), pill(Theme.TEXT, 0))
                addState(intArrayOf(), pill(0x1AFFFFFF, Theme.STROKE))
            }
            setOnClickListener { onClick() }
            applyFocus(false)
        }

        private fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat()
            if (stroke != 0) setStroke(context.dp(1), stroke)
        }

        private fun applyFocus(f: Boolean) {
            val c = if (f) Theme.TEXT_DARK else Theme.TEXT
            label.setTextColor(c)
            iconView.imageTintList = ColorStateList.valueOf(c)
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            applyFocus(gainFocus)
            animate().scaleX(if (gainFocus) 1.06f else 1f).scaleY(if (gainFocus) 1.06f else 1f)
                .setDuration(TileSpec.FOCUS_MS).start()
        }
    }
}
