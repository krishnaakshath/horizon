package dev.tvlauncher.network

import android.content.Context
import android.view.ViewGroup
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelShell

/**
 * Wi-Fi / Ethernet panel in the shared [PanelShell] anatomy; content comes from [WifiPanelModel].
 * The host calls [show] when the Wi-Fi pill is activated, forwards BACK to [onBack] and restores
 * focus to the pill after the panel closes. Joining a network or switching Wi-Fi is not possible
 * for apps targeting API 29+, so those rows open the TV's own Wi-Fi screen (the last row says so).
 */
class NetworkPanel(
    private val context: Context,
    private val controller: WifiController,
    lite: Boolean
) {
    private val shell = PanelShell(context, lite)
    private var startedHere = false
    private var last: WifiSnapshot? = null
    private val listener = WifiController.Listener { render(it) }

    /** True from [show] until the close animation has finished. */
    val isShowing: Boolean get() = shell.isVisible

    /** True while open and accepting input (false once the close animation has started). */
    val isOpen: Boolean get() = shell.isOpen

    init {
        shell.onRowClick = ::onRow
    }

    fun show(host: ViewGroup) {
        if (shell.isOpen) return
        if (!controller.isStarted) { controller.start(); startedHere = true }
        last = null
        shell.show(host)
        controller.addListener(listener)
        controller.scanIfStale()
    }

    fun dismiss() {
        if (!shell.isOpen) return
        detach()
        shell.dismiss()
    }

    /**
     * Immediate teardown, no animation: removes the view, drops the listener and stops the
     * controller if this panel started it. Idempotent. Call from the host's onStop/onDestroy.
     */
    fun release() {
        detach()
        shell.release()
    }

    private fun detach() {
        controller.removeListener(listener)
        if (startedHere) { controller.stop(); startedHere = false }
    }

    /** Consumed while the panel is open or still animating out. */
    fun onBack(): Boolean {
        if (shell.isOpen) { dismiss(); return true }
        return shell.isVisible
    }

    private fun render(s: WifiSnapshot) {
        if (!shell.isOpen || s == last) return
        last = s
        shell.render(WifiPanelModel.build(s))
    }

    private fun onRow(row: PanelEntry.Row) {
        when (row.key) {
            WifiPanelModel.KEY_SCAN -> controller.scan()
            WifiPanelModel.KEY_LOCATION -> NetworkSettings.openLocation(context)
            // The switch, every network and the last row: the TV's Wi-Fi screen does the work.
            else -> NetworkSettings.openWifi(context)
        }
    }
}
