package dev.tvlauncher.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.tvlauncher.network.BluetoothController
import dev.tvlauncher.network.BtPanelModel
import dev.tvlauncher.network.BtSnapshot
import dev.tvlauncher.network.PairState
import dev.tvlauncher.panel.PanelEntry

/**
 * The Bluetooth screen's state and row handling, drawn into a [PanelSink]: the home Bluetooth
 * panel and Settings > Bluetooth share it. Pairing is real (createBond); Connect, Disconnect and
 * Forget are tried in the launcher and end in one calm line when the TV refuses. [open] when
 * shown, [close] when hidden (cancels discovery); [onBack] leaves the device view first.
 */
class BluetoothScreen(
    private val context: Context,
    private val controller: BluetoothController,
    private val sink: PanelSink
) {
    private val main = Handler(Looper.getMainLooper())
    private var startedHere = false
    private var latest: BtSnapshot? = null
    private var selected: String? = null
    private var notice: String? = null
    private var lastRenderAt = 0L
    private val listener = BluetoothController.Listener { latest = it; schedule() }
    private val renderRunnable = Runnable { render() }

    fun open() {
        if (!controller.isStarted) { controller.start(); startedHere = true }
        selected = null; notice = null; lastRenderAt = 0L
        controller.addListener(listener)
        if (controller.snapshot.enabled && !controller.snapshot.scanning) controller.startDiscovery()
    }

    fun close() {
        main.removeCallbacks(renderRunnable)
        controller.removeListener(listener)
        controller.stopDiscovery(publish = false)
        if (startedHere) { controller.stop(); startedHere = false }
    }

    fun onBack(): Boolean {
        val from = selected ?: return false
        selected = null; notice = null
        render(preferKey = "bt:$from")
        return true
    }

    /** Coalesces bursts (ACTION_FOUND) to at most one rebuild per 250 ms. */
    private fun schedule() {
        main.removeCallbacks(renderRunnable)
        val wait = (lastRenderAt + 250 - SystemClock.uptimeMillis()).coerceAtLeast(0)
        main.postDelayed(renderRunnable, wait)
    }

    fun render(preferKey: String? = null) {
        val s = latest ?: return
        if (!sink.active) return
        lastRenderAt = SystemClock.uptimeMillis()
        val sel = selected?.let { a -> s.sections.all.firstOrNull { it.address.equals(a, true) && it.bonded } }
        if (selected != null && sel == null) selected = null
        if (sel != null) sink.render(BtPanelModel.device(sel, notice),
            if (sel.connected) BtPanelModel.KEY_DISCONNECT else BtPanelModel.KEY_CONNECT)
        else sink.render(BtPanelModel.build(s, notice), preferKey)
    }

    fun onRow(row: PanelEntry.Row) {
        val s = latest ?: return
        when {
            row.key == BtPanelModel.KEY_TOGGLE -> {
                notice = null
                // Discovery starts from the controller's STATE_ON broadcast, not a fixed delay.
                if (controller.setEnabled(!s.enabled, scanWhenReady = true) != BluetoothController.Result.OK) {
                    notice = "This TV didn't let the launcher switch Bluetooth."
                    render()
                }
            }
            row.key == BtPanelModel.KEY_SCAN -> if (s.scanning) controller.stopDiscovery() else controller.startDiscovery()
            row.key == BtPanelModel.KEY_LOCATION -> TvScreens.open(context, TvScreens.Screen.LOCATION)
            row.key == BtPanelModel.KEY_PERMISSION -> {
                if (!SystemPermissions.requestLocation(context)) { notice = "Location access can't be asked for right now."; render() }
            }
            row.key == BtPanelModel.KEY_FORGET -> forget()
            row.key == BtPanelModel.KEY_CONNECT -> link(connect = true)
            row.key == BtPanelModel.KEY_DISCONNECT -> link(connect = false)
            row.key.startsWith("bt:") -> onDevice(s, row.key.removePrefix("bt:"))
        }
    }

    private fun onDevice(s: BtSnapshot, address: String) {
        val d = s.sections.all.firstOrNull { it.address.equals(address, true) } ?: return
        val pair = s.pairing[d.address] ?: PairState.IDLE
        notice = null
        if (d.bonded) {
            selected = d.address
            render()
        } else if (pair != PairState.PAIRING) {
            if (controller.pair(d.address) == BluetoothController.Result.NOT_PERMITTED) {
                notice = "This TV didn't let the launcher pair ${d.name}."
                render()
            }
        }
    }

    private fun nameOf(address: String) =
        latest?.sections?.all?.firstOrNull { it.address.equals(address, true) }?.name ?: "the device"

    private fun forget() {
        val address = selected ?: return
        val name = nameOf(address)
        notice = when (controller.forget(address)) {
            BluetoothController.Result.OK -> "Forgetting $name…"
            else -> "This TV didn't let the launcher forget $name."
        }
        selected = null
        render()
    }

    private fun link(connect: Boolean) {
        val address = selected ?: return
        val name = nameOf(address)
        val r = if (connect) controller.connect(address) else controller.disconnect(address)
        notice = when {
            r == BluetoothController.Result.OK -> if (connect) "Connecting to $name…" else "Disconnecting $name…"
            connect -> "Switch $name on; it connects by itself."
            else -> "This TV didn't let the launcher disconnect $name. Switch it off to disconnect."
        }
        render()
    }
}
