package dev.tvlauncher.network

import android.content.Context
import android.view.ViewGroup
import dev.tvlauncher.panel.PanelShell
import dev.tvlauncher.system.ShellSink
import dev.tvlauncher.system.WifiScreen

/**
 * Wi-Fi / Ethernet panel in the shared [PanelShell] anatomy; content and actions come from
 * [WifiScreen] (the same screen Settings > Wi-Fi embeds). The host calls [show] when the Wi-Fi
 * pill is activated, forwards BACK to [onBack] and restores focus to the pill after it closes.
 * Joining, switching, Reconnect and Forget all run in the launcher.
 */
class NetworkPanel(
    context: Context,
    controller: WifiController,
    lite: Boolean
) {
    private val shell = PanelShell(context, lite)
    private var host: ViewGroup? = null
    private val screen = WifiScreen(context, controller, lite, ShellSink(shell)) { host }

    /** True from [show] until the close animation has finished. */
    val isShowing: Boolean get() = shell.isVisible

    /** True while open and accepting input (false once the close animation has started). */
    val isOpen: Boolean get() = shell.isOpen

    init {
        shell.onRowClick = screen::onRow
    }

    fun show(host: ViewGroup) {
        if (shell.isOpen) return
        this.host = host
        shell.show(host)
        screen.open()
    }

    fun dismiss() {
        if (!shell.isOpen) return
        screen.close()
        shell.dismiss()
    }

    /** Immediate teardown, no animation. Idempotent. Call from the host's onStop/onDestroy. */
    fun release() {
        screen.close()
        shell.release()
    }

    /** BACK closes the password sheet, then leaves the network view, then closes the panel. */
    fun onBack(): Boolean {
        if (!shell.isOpen) return shell.isVisible
        if (!screen.onBack()) dismiss()
        return true
    }
}
