package dev.tvlauncher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.home.WeatherChip
import dev.tvlauncher.network.NetIcons
import dev.tvlauncher.network.SelectableStatusPill
import dev.tvlauncher.settings.AppearanceFormat
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.ui.Theme.style

/**
 * Clock + date on the left; on the right a row of selectable pills: Bluetooth (only when the TV
 * has an adapter), network (Ethernet, or Wi-Fi level 0..4), Inputs and Settings. OK on the status
 * pills calls [onBluetooth] / [onNetwork] with the pill, so the host can return focus to it.
 * D-pad rules come from [TopBarNav]: DOWN is reported via [onDown], LEFT/RIGHT stay in the row and
 * UP from below returns to the pill focused last. [startStatus] in onStart, [stopStatus] in onStop.
 */
class TopBar(
    context: Context,
    onNetwork: (View) -> Unit,
    onBluetooth: (View) -> Unit,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    onSettingsLongPress: () -> Unit,
    onDown: () -> Boolean
) : LinearLayout(context) {
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
    val bluetoothPill = SelectableStatusPill(context, NetIcons.forBluetooth(NetIcons.BtState.ON)) {}
        .apply { setOnClickListener { onBluetooth(this) } }
    val networkPill = SelectableStatusPill(context, NetIcons.forWifiLevel(0, false)) {}
        .apply { setOnClickListener { onNetwork(this) } }
    val inputsButton = PillButton(context, R.drawable.ic_input, "Inputs", onInputs)
    val settingsButton = PillButton(context, R.drawable.ic_settings, "Settings", onSettings).apply {
        setOnLongClickListener { onSettingsLongPress(); true }
    }
    private val pills: List<View> = listOf(bluetoothPill, networkPill, inputsButton, settingsButton)
    private var lastPill: View? = null
    private var statusVisible = true
    private var hasBluetooth = false
    private val status = StatusIcons(context) { s ->
        networkPill.setIcon(StatusModel.networkPillIcon(s))
        networkPill.contentDescription = StatusModel.networkLabel(s)
        val bt = StatusModel.bluetoothPillIcon(s)
        hasBluetooth = bt != null
        if (bt != null) bluetoothPill.setIcon(bt)
        bluetoothPill.contentDescription = "Bluetooth " + (s.bt?.name?.lowercase() ?: "")
        updateStatusVisibility()
    }
    private val weatherIcon = ImageView(context)
    private val weatherText = TextView(context).apply { style(Theme.SP_DATE, Theme.regular, Theme.TEXT) }
    /** Compact, never focusable weather chip next to the clock (HomeWeather feeds it). */
    private val weather = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        visibility = View.GONE
        addView(weatherIcon, LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(6) })
        addView(weatherText)
    }
    private val dot = View(context).apply {
        background = GradientDrawable().apply { setColor(Theme.TEXT_2); shape = GradientDrawable.OVAL }
        alpha = 0.6f
    }

    /** Focus mode is on: a tiny accent dot after the date (never focusable). */
    private val focusDot = View(context).apply {
        visibility = View.GONE
        isFocusable = false
        contentDescription = "Focus mode on"
    }

    /** Focus mode: the dot in [accent] and a dimmed clock while on. */
    fun setFocus(on: Boolean, accent: Int) {
        focusDot.background = GradientDrawable().apply { setColor(BrandColors.backdropAccent(accent)); shape = GradientDrawable.OVAL }
        focusDot.visibility = if (on) View.VISIBLE else View.GONE
        clock.alpha = if (on) dev.tvlauncher.focus.FocusModel.CLOCK_DIM_ALPHA else 1f
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(weather, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.dp(Theme.S4) })
        addView(dot, LayoutParams(context.dp(4), context.dp(4)).apply {
            marginStart = context.dp(Theme.S3); marginEnd = context.dp(Theme.S3)
        })
        addView(focusDot, LayoutParams(context.dp(8), context.dp(8)).apply { marginEnd = context.dp(Theme.S3) })
        addView(date, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        for (pill in pills) {
            addView(pill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(Theme.S3)
            })
            pill.setOnKeyListener { v, code, ev -> ev.action == KeyEvent.ACTION_DOWN && onPillKey(v, code, onDown) }
            pill.setOnFocusChangeListener { v, has -> if (has) lastPill = v }
        }
        bluetoothPill.visibility = View.GONE // until the first status read finds an adapter
    }

    private fun visiblePills() = pills.filter { it.visibility == View.VISIBLE }

    private fun onPillKey(v: View, code: Int, onDown: () -> Boolean): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            val shown = visiblePills()
            TopBarNav.onHorizontal(shown.indexOf(v), shown.size, code == KeyEvent.KEYCODE_DPAD_RIGHT) ==
                TopBarNav.Horizontal.CONSUME
        }
        else -> false
    }

    /** UP from the shelf (or anything below) lands on the pill focused last. */
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (direction == View.FOCUS_UP && !hasFocus()) {
            val target = TopBarNav.upTarget(lastPill, visiblePills())
            if (target != null) { views.add(target); return }
        }
        super.addFocusables(views, direction, focusableMode)
    }

    private fun updateStatusVisibility() {
        val btShown = statusVisible && hasBluetooth
        if (!btShown && bluetoothPill.hasFocus()) (if (statusVisible) networkPill else inputsButton).requestFocus()
        if (!statusVisible && networkPill.hasFocus()) inputsButton.requestFocus()
        bluetoothPill.visibility = if (btShown) View.VISIBLE else View.GONE
        networkPill.visibility = if (statusVisible) View.VISIBLE else View.GONE
    }

    /** Weather chip; independent of the status-icons appearance toggle. */
    fun showWeather(chip: WeatherChip) {
        weather.visibility = if (chip.visible) View.VISIBLE else View.GONE
        if (!chip.visible) return
        chip.icon?.let { weatherIcon.setImageResource(WeatherCompanion.drawable(it)) }
        weatherIcon.visibility = if (chip.icon != null) View.VISIBLE else View.GONE
        weatherText.text = chip.text
        weatherText.setTextColor(if (chip.hint) Theme.TEXT_2 else Theme.TEXT)
        weather.contentDescription = chip.description
    }

    /** Settings > Appearance: clock format, date and status icons. */
    fun applyAppearance(a: AppearanceSettings) {
        val is24h = AppearanceFormat.is24h(a.clockFormat, android.text.format.DateFormat.is24HourFormat(context))
        val pattern = if (is24h) AppearanceFormat.PATTERN_24H else AppearanceFormat.PATTERN_12H
        clock.format12Hour = pattern
        clock.format24Hour = pattern // the user's choice wins over the system 12/24-hour setting
        date.visibility = if (a.showDate) View.VISIBLE else View.INVISIBLE // keeps the weight-1 spacer
        dot.visibility = if (a.showDate) View.VISIBLE else View.GONE
        statusVisible = a.showStatusIcons
        updateStatusVisibility()
    }

    private var statusRunning = false

    /** Idempotent and paired with [stopStatus], whatever path onStart/onStop took. */
    fun startStatus() {
        if (statusRunning) return
        statusRunning = true
        status.start()
    }

    fun stopStatus() {
        if (!statusRunning) return
        statusRunning = false
        status.stop()
    }

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
                addState(intArrayOf(), Glass.clear(context, false, Theme.RADIUS_PILL))
            }
            Glass.track(this)
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
                .setDuration(Motion.ms(TileSpec.FOCUS_MS)).start()
        }
    }
}
