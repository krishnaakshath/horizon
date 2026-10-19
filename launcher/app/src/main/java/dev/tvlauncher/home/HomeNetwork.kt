package dev.tvlauncher.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import dev.tvlauncher.network.BluetoothController
import dev.tvlauncher.network.BluetoothPanel
import dev.tvlauncher.network.NetworkPanel
import dev.tvlauncher.network.WifiController

/**
 * The home screen's Wi-Fi/Ethernet and Bluetooth panels, hosted in the root FrameLayout [host].
 * One controller of each drives its panel; at most one panel is open at a time and BACK closes it
 * (the Bluetooth panel first leaves its device-action view). Focus returns to the view that opened
 * the panel (a top-bar pill), else to [fallbackFocus] (e.g. opened from Spotlight).
 *
 * Lifecycle: [start] in onStart starts both controllers; [stop] in onStop releases any open panel
 * without animation and stops the controllers; [release] in onDestroy. [onOpenChanged] reports
 * open/closed so the host can pause the ambient idle timer and keep Spotlight away.
 */
class HomeNetwork(
    context: Context,
    private val host: ViewGroup,
    lite: Boolean,
    private val fallbackFocus: () -> Unit,
    private val onOpenChanged: (Boolean) -> Unit
) {
    private val wifi = WifiController(context)
    private val bt = BluetoothController(context)
    private val wifiPanel = NetworkPanel(context, wifi, lite)
    private val btPanel = BluetoothPanel(context, bt, lite)
    private var opener: View? = null
    private var open = false

    /** True from show until the close animation ends (Spotlight must stay closed meanwhile). */
    val isShowing: Boolean get() = wifiPanel.isShowing || btPanel.isShowing
    val hasBluetooth: Boolean get() = bt.isPresent

    fun start() {
        wifi.start()
        bt.start()
    }

    fun stop() {
        release()
        wifi.stop()
        bt.stop()
    }

    /** Immediate teardown of both panels (idempotent). */
    fun release() {
        wifiPanel.release()
        btPanel.release()
        closed(restoreFocus = true)
    }

    /** Opens the Wi-Fi panel; false when another panel is still showing. */
    fun showWifi(from: View?): Boolean = show(from) { wifiPanel.show(host) }

    /** Opens the Bluetooth panel; false without an adapter (caller falls back to system settings). */
    fun showBluetooth(from: View?): Boolean = bt.isPresent && show(from) { btPanel.show(host) }

    private inline fun show(from: View?, block: () -> Unit): Boolean {
        if (isShowing) return false
        opener = from
        block()
        open = true
        onOpenChanged(true)
        return true
    }

    /** BACK: true when a panel consumed it (also while one is animating out). */
    fun onBack(): Boolean {
        val consumed = wifiPanel.onBack() || btPanel.onBack()
        if (open && !wifiPanel.isOpen && !btPanel.isOpen) closed(restoreFocus = true)
        return consumed
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
}
