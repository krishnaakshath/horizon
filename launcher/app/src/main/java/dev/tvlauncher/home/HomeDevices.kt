package dev.tvlauncher.home

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.devices.DeviceActions
import dev.tvlauncher.devices.DeviceAnnouncePolicy
import dev.tvlauncher.devices.DeviceEvent
import dev.tvlauncher.devices.DeviceMonitor
import dev.tvlauncher.devices.DeviceToastHost
import dev.tvlauncher.devices.DevicesPanel
import dev.tvlauncher.devices.DevicesPanelModel
import dev.tvlauncher.devices.InputsPanel
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelShell
import dev.tvlauncher.ui.SystemActions

/**
 * Device announcements, the Devices panel and the Inputs panel on the home screen. Owns one
 * [DeviceMonitor] and adds a [DeviceToastHost] on top of [root]. Cards follow
 * [DeviceAnnouncePolicy] (no Wi-Fi, Ethernet plug/unplug only, quiet during the splash and the
 * first 5 s); each card's sound plays through [HomeSounds] when the card appears.
 *
 * Both panels use the shared panel anatomy; one is open at a time, BACK closes it and focus goes
 * back to the view that opened it, else to [fallbackFocus].
 *
 * Lifecycle: [start] in onStart (monitor seeds silently, then listens); [stop] in onStop (monitor
 * stopped, cards cleared, panels closed). [onOpenChanged] reports the panels for the idle timer.
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
    private val lite = mode == EffectsMode.LITE
    private val toasts = DeviceToastHost(context, mode).also {
        root.addView(it, FrameLayout.LayoutParams(-1, -1))
        // The sound plays when a card really appears, so queued or coalesced offers stay silent.
        it.onCardShown = { e -> sounds.device(DeviceAnnouncePolicy.sound(e, newCard = true)) }
    }
    private val monitor = DeviceMonitor(context, ::onEvent)
    private val devicesShell = PanelShell(context, lite).also { it.onRowClick = ::onDeviceRow }
    private val inputs = InputsPanel(context, lite, ::inputNames).also {
        it.onSwitched = { it.release(); closed(restoreFocus = true) }
    }
    private var startedAt = 0L
    private var notice: String? = null
    private var opener: View? = null
    private var open = false

    /** True from opening until a close animation has finished. */
    val isShowing: Boolean get() = devicesShell.isVisible || inputs.isShowing

    fun start() {
        startedAt = SystemClock.uptimeMillis()
        monitor.start()
    }

    fun stop() {
        release()
        monitor.stop()
        toasts.clear()
    }

    private fun onEvent(e: DeviceEvent) {
        val since = SystemClock.uptimeMillis() - startedAt
        if (!DeviceAnnouncePolicy.showCard(e, since, splashRunning())) return
        toasts.show(e)
    }

    /** Opens the Devices panel; false when a panel is already showing. */
    fun showPanel(from: View? = null): Boolean {
        if (isShowing) return false
        opener = from
        notice = null
        devicesShell.show(root)
        renderDevices()
        monitor.onChanged = { renderDevices() }
        opened()
        return true
    }

    /** Opens the Inputs panel; false when a panel is showing or the TV has no inputs. */
    fun showInputs(from: View? = null): Boolean {
        if (isShowing || !inputs.hasInputs) return false
        opener = from
        inputs.show(root)
        opened()
        return true
    }

    /** Keeps device cards above any panel scrim added to the root later. */
    fun toastsToFront() = toasts.bringToFront()

    /** BACK: true when a panel consumed it (also while one is animating out). */
    fun onBack(): Boolean {
        val consumed = when {
            devicesShell.isOpen -> { monitor.onChanged = null; devicesShell.dismiss(); true }
            inputs.isOpen -> inputs.onBack()
            else -> isShowing
        }
        if (open && !devicesShell.isOpen && !inputs.isOpen) closed(restoreFocus = true)
        return consumed
    }

    /** Immediate teardown of both panels (idempotent). */
    fun release() {
        monitor.onChanged = null
        devicesShell.release()
        inputs.release()
        closed(restoreFocus = true)
    }

    private fun opened() {
        open = true
        toastsToFront()
        onOpenChanged(true)
    }

    private fun closed(restoreFocus: Boolean) {
        if (!open) return
        open = false
        val from = opener
        opener = null
        if (restoreFocus) {
            val back = from?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }?.requestFocus() ?: false
            if (!back) fallbackFocus()
        }
        onOpenChanged(false)
    }

    private fun renderDevices() {
        if (devicesShell.isOpen) devicesShell.render(DevicesPanelModel.build(monitor.devices(), notice))
    }

    private fun onDeviceRow(row: PanelEntry.Row) {
        if (row.key == DevicesPanelModel.KEY_SETTINGS) { SystemActions.launcherSettings(context, "devices"); return }
        val d = monitor.devices().firstOrNull { DevicesPanelModel.PREFIX + it.id == row.key } ?: return
        val inputId = DeviceActions.passthroughInputId(d) ?: return
        if (DevicesPanel.switchTo(context, inputId)) {
            release()
        } else {
            notice = "Couldn't switch to ${row.label}."
            renderDevices()
        }
    }

    /** Known device names per TV input id, from the monitor's HDMI port tracking. */
    private fun inputNames(): Map<String, String> =
        monitor.devices().mapNotNull { d -> DeviceActions.passthroughInputId(d)?.let { it to d.name } }.toMap()
}
