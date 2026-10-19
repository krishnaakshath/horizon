package dev.tvlauncher.network

import android.content.Context
import android.view.ViewGroup
import dev.tvlauncher.panel.PanelShell
import dev.tvlauncher.system.BluetoothScreen
import dev.tvlauncher.system.ShellSink

/**
 * Bluetooth panel in the shared [PanelShell] anatomy; content and actions come from
 * [BluetoothScreen] (the same screen Settings > Bluetooth embeds). The host calls [show], forwards
 * BACK to [onBack] and restores focus to the pill afterwards.
 */
class BluetoothPanel(
    context: Context,
    controller: BluetoothController,
    lite: Boolean
) {
    private val shell = PanelShell(context, lite)
    private val screen = BluetoothScreen(context, controller, ShellSink(shell))

    /** True from [show] until the close animation has finished. */
    val isShowing: Boolean get() = shell.isVisible

    /** True while open and accepting input (false once the close animation has started). */
    val isOpen: Boolean get() = shell.isOpen

    init {
        shell.onRowClick = screen::onRow
    }

    fun show(host: ViewGroup) {
        if (shell.isOpen) return
        shell.show(host)
        screen.open()
    }

    fun dismiss() {
        if (!shell.isOpen) return
        screen.close()
        shell.dismiss()
    }

    /** Immediate teardown, no animation (cancels discovery, stops the controller if started here). Idempotent. */
    fun release() {
        screen.close()
        shell.release()
    }

    /** BACK leaves the device view first, then closes the panel; consumed while animating out. */
    fun onBack(): Boolean {
        if (!shell.isOpen) return shell.isVisible
        if (!screen.onBack()) dismiss()
        return true
    }
}
