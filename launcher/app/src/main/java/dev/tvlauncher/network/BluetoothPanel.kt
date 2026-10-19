package dev.tvlauncher.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelShell

/**
 * Bluetooth panel in the shared [PanelShell] anatomy; content comes from [BtPanelModel]. The host
 * calls [show], forwards BACK to [onBack] and restores focus to the pill afterwards. Pairing is
 * real (createBond); connect/disconnect is not public API, so after a bond the system connects
 * by itself and the TV's Bluetooth settings (always the last row) handle the rest.
 */
class BluetoothPanel(
    private val context: Context,
    private val controller: BluetoothController,
    lite: Boolean
) {
    private val shell = PanelShell(context, lite)
    private val main = Handler(Looper.getMainLooper())
    private var startedHere = false
    private var latest: BtSnapshot? = null
    private var selected: String? = null
    private var notice: String? = null
    private var lastRenderAt = 0L
    private val listener = BluetoothController.Listener { latest = it; schedule() }
    private val renderRunnable = Runnable { rerender() }

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
        selected = null; notice = null; lastRenderAt = 0L
        shell.show(host)
        controller.addListener(listener)
        if (controller.snapshot.enabled && !controller.snapshot.scanning) controller.startDiscovery()
    }

    fun dismiss() {
        if (!shell.isOpen) return
        detach()
        shell.dismiss()
    }

    /**
     * Immediate teardown, no animation: removes the view, cancels pending renders and discovery,
     * drops the listener and stops the controller if this panel started it. Idempotent.
     */
    fun release() {
        detach()
        shell.release()
    }

    private fun detach() {
        main.removeCallbacks(renderRunnable)
        controller.removeListener(listener)
        controller.stopDiscovery(publish = false)
        if (startedHere) { controller.stop(); startedHere = false }
    }

    /** BACK leaves the device view first, then closes the panel; consumed while animating out. */
    fun onBack(): Boolean {
        if (!shell.isOpen) return shell.isVisible
        if (selected != null) {
            val from = selected
            selected = null
            rerender(preferKey = from?.let { "bt:$it" })
        } else {
            dismiss()
        }
        return true
    }

    /** Coalesces bursts (ACTION_FOUND) to at most one rebuild per 250 ms. */
    private fun schedule() {
        main.removeCallbacks(renderRunnable)
        val wait = (lastRenderAt + 250 - SystemClock.uptimeMillis()).coerceAtLeast(0)
        main.postDelayed(renderRunnable, wait)
    }

    private fun rerender(preferKey: String? = null) {
        val s = latest ?: return
        if (!shell.isOpen) return
        lastRenderAt = SystemClock.uptimeMillis()
        val sel = selected?.let { a -> s.sections.all.firstOrNull { it.address.equals(a, true) && it.bonded } }
        if (selected != null && sel == null) selected = null
        if (sel != null) shell.render(BtPanelModel.device(sel), BtPanelModel.KEY_FORGET)
        else shell.render(BtPanelModel.build(s, notice), preferKey)
    }

    private fun onRow(row: PanelEntry.Row) {
        val s = latest ?: return
        when {
            row.key == BtPanelModel.KEY_TOGGLE -> {
                notice = null
                // Discovery starts from the controller's STATE_ON broadcast, not a fixed delay.
                if (controller.setEnabled(!s.enabled, scanWhenReady = true) != BluetoothController.Result.OK) {
                    notice = "This TV doesn't let apps switch Bluetooth. Use Bluetooth settings."
                    rerender()
                }
            }
            row.key == BtPanelModel.KEY_SCAN -> if (s.scanning) controller.stopDiscovery() else controller.startDiscovery()
            row.key == BtPanelModel.KEY_LOCATION -> NetworkSettings.openLocation(context)
            row.key == BtPanelModel.KEY_ALLOW_LOCATION -> NetworkSettings.requestLocation(context)
            row.key == BtPanelModel.KEY_SETTINGS -> NetworkSettings.openBluetooth(context)
            row.key == BtPanelModel.KEY_FORGET -> forget()
            row.key.startsWith("bt:") -> onDevice(s, row.key.removePrefix("bt:"))
        }
    }

    private fun onDevice(s: BtSnapshot, address: String) {
        val d = s.sections.all.firstOrNull { it.address.equals(address, true) } ?: return
        val pair = s.pairing[d.address] ?: PairState.IDLE
        notice = null
        if (d.bonded) {
            selected = d.address
            rerender()
        } else if (pair != PairState.PAIRING) {
            if (controller.pair(d.address) == BluetoothController.Result.NOT_PERMITTED) {
                notice = "Pairing isn't allowed from here. Use Bluetooth settings."
                rerender()
            }
        }
    }

    private fun forget() {
        val address = selected ?: return
        val name = latest?.sections?.all?.firstOrNull { it.address.equals(address, true) }?.name ?: "the device"
        notice = when (controller.forget(address)) {
            BluetoothController.Result.OK -> "Forgetting $name…"
            else -> {
                NetworkSettings.openBluetooth(context)
                "Forget $name in Bluetooth settings."
            }
        }
        selected = null
        rerender()
    }
}
