package dev.tvlauncher.home

import android.content.Context
import android.os.SystemClock
import android.view.FocusFinder
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.devices.DeviceAnnouncePolicy
import dev.tvlauncher.devices.DeviceEvent
import dev.tvlauncher.devices.DeviceMonitor
import dev.tvlauncher.devices.DeviceToastHost
import dev.tvlauncher.devices.DevicesPanel
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * Device announcements and the Devices panel on the home screen. Owns one [DeviceMonitor] and
 * adds a [DeviceToastHost] on top of [root]. Cards follow [DeviceAnnouncePolicy] (no Wi-Fi, Ethernet
 * plug/unplug only, quiet during the splash and the first 5 s); sounds go through [HomeSounds].
 * The panel is a focus-trapping overlay; BACK closes it and focus goes to [fallbackFocus].
 *
 * Lifecycle: [start] in onStart (monitor seeds silently, then listens); [stop] in onStop (monitor
 * stopped, cards cleared, panel closed). [onOpenChanged] reports the panel for the idle timer.
 */
class HomeDevices(
    private val context: Context,
    private val root: FrameLayout,
    mode: EffectsMode,
    private val sounds: HomeSounds,
    private val splashRunning: () -> Boolean,
    private val fallbackFocus: () -> Unit,
    private val onOpenChanged: (Boolean) -> Unit
) {
    private val toasts = DeviceToastHost(context, mode).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
    private val monitor = DeviceMonitor(context, ::onEvent)
    private var startedAt = 0L
    private var overlay: FrameLayout? = null

    val isShowing: Boolean get() = overlay != null

    fun start() {
        startedAt = SystemClock.uptimeMillis()
        monitor.start()
    }

    fun stop() {
        closePanel()
        monitor.stop()
        toasts.clear()
    }

    private fun onEvent(e: DeviceEvent) {
        val since = SystemClock.uptimeMillis() - startedAt
        if (!DeviceAnnouncePolicy.showCard(e, since, splashRunning())) return
        sounds.device(DeviceAnnouncePolicy.sound(e, newCard = toasts.show(e)))
    }

    /** Opens the panel over everything; false when it is already open. */
    fun showPanel(): Boolean {
        if (overlay != null) return false
        val panel = DevicesPanel(context)
        panel.setDevices(monitor.devices())
        panel.onSwitchResult = { ok -> if (ok) closePanel() else Toast.makeText(context, "Could not open input", Toast.LENGTH_SHORT).show() }
        val o = object : FrameLayout(context) {
            /** Focus trap: D-pad never leaves the panel while it is open. */
            override fun focusSearch(focused: View?, direction: Int): View? =
                FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
        }
        o.setBackgroundColor(SCRIM)
        o.isClickable = true
        o.isFocusable = true // holds focus when no row is focusable
        o.descendantFocusability = FrameLayout.FOCUS_AFTER_DESCENDANTS
        o.addView(panel, FrameLayout.LayoutParams(context.dp(PANEL_W), -2, Gravity.TOP or Gravity.END).apply {
            topMargin = context.dp(Theme.MARGIN_V); bottomMargin = context.dp(Theme.MARGIN_V); marginEnd = context.dp(Theme.MARGIN_H)
        })
        root.addView(o, FrameLayout.LayoutParams(-1, -1))
        overlay = o
        monitor.onChanged = { panel.setDevices(monitor.devices()) }
        o.requestFocus()
        onOpenChanged(true)
        return true
    }

    /** BACK: true when the panel was open and is now closed. */
    fun onBack(): Boolean = closePanel()

    private fun closePanel(): Boolean {
        val o = overlay ?: return false
        overlay = null
        monitor.onChanged = null
        root.removeView(o)
        fallbackFocus()
        onOpenChanged(false)
        return true
    }

    private companion object {
        const val PANEL_W = 560
        const val SCRIM = 0x99000000.toInt()
    }
}
