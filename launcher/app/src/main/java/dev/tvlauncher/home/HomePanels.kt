package dev.tvlauncher.home

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import dev.tvlauncher.ui.QuickActions

/**
 * Coordinates the home screen's overlay panels: Wi-Fi/Ethernet and Bluetooth ([HomeNetwork]),
 * Devices and Inputs ([HomeDevices]). One panel at a time; Spotlight closes (without animation) before a panel
 * opens, and the host keeps Spotlight closed while [isShowing]. Also routes Spotlight's quick
 * actions ([QuickActions.route]).
 *
 * Lifecycle: [start] in onStart, [stop] in onStop (panels closed, controllers and the device
 * monitor stopped), [release] in onDestroy. [onOpenChanged] feeds the ambient idle timer.
 */
class HomePanels(
    context: Context,
    root: FrameLayout,
    look: HomeLook,
    private val sounds: HomeSounds,
    splashRunning: () -> Boolean,
    private val closeSpotlight: () -> Unit,
    focusHome: () -> Unit,
    onOpenChanged: (Boolean) -> Unit
) {
    val net = HomeNetwork(context, root, lite = !look.profile.full, fallbackFocus = focusHome, onOpenChanged = onOpenChanged)
    val devices = HomeDevices(context, root, look.profile.effectsMode, sounds, splashRunning, focusHome, onOpenChanged)

    val isShowing: Boolean get() = net.isShowing || devices.isShowing

    fun start() {
        net.start()
        devices.start()
    }

    fun stop() {
        net.stop()
        devices.stop()
    }

    /** HOME press / onDestroy: close everything at once. */
    fun release() {
        net.release()
        devices.release()
    }

    fun onBack(): Boolean = net.onBack() || devices.onBack()

    fun openWifi(from: View?) = open { net.showWifi(from) }
    fun openBluetooth(from: View?) = open { net.showBluetooth(from) }
    fun openDevices(from: View?) = open { devices.showPanel(from) }
    /** False when the TV has no passthrough inputs (the caller then uses the system input switch). */
    fun openInputs(from: View?) = open { devices.showInputs(from) }

    /** True when the item opened a panel or ambient here; false means "use SystemActions". */
    fun quickAction(item: QuickActions.Item, startAmbient: () -> Boolean): Boolean =
        when (QuickActions.route(item, net.hasBluetooth)) {
            QuickActions.Route.WIFI_PANEL -> openWifi(null)
            QuickActions.Route.BLUETOOTH_PANEL -> openBluetooth(null)
            QuickActions.Route.DEVICES_PANEL -> openDevices(null)
            QuickActions.Route.INPUTS_PANEL -> openInputs(null)
            QuickActions.Route.AMBIENT -> { sounds.action(startAmbient()); true } // handled here, even on failure
            QuickActions.Route.ROOM_CYCLE, QuickActions.Route.DISCOVER, QuickActions.Route.RESTART, QuickActions.Route.SYSTEM -> false
        }

    private inline fun open(block: () -> Boolean): Boolean {
        if (isShowing) return false // one panel at a time
        closeSpotlight()
        return block().also { if (it) { sounds.panel(opened = true); devices.toastsToFront() } }
    }
}
