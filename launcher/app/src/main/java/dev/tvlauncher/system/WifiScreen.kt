package dev.tvlauncher.system

import android.content.Context
import android.view.ViewGroup
import dev.tvlauncher.network.WifiController
import dev.tvlauncher.network.WifiPanelModel
import dev.tvlauncher.network.WifiSnapshot
import dev.tvlauncher.panel.PanelEntry

/**
 * The Wi-Fi screen's state and row handling, drawn into a [PanelSink]: the home Wi-Fi panel and
 * Settings > Wi-Fi share it. List view ([WifiPanelModel.build]) and one network's view
 * ([WifiPanelModel.detail]); actions run through [WifiFlow]. Call [open] when shown and [close]
 * when hidden; [onBack] steps back (sheet, then network view) and returns false at the top.
 */
class WifiScreen(
    private val context: Context,
    private val controller: WifiController,
    lite: Boolean,
    private val sink: PanelSink,
    private val host: () -> ViewGroup?
) {
    private var startedHere = false
    private var last: WifiSnapshot? = null
    private var selected: String? = null
    private var notice: String? = null
    private val listener = WifiController.Listener { s -> if (s != last) { last = s; render() } }
    private val flow = WifiFlow(context, controller, lite, host, { notice = it }, { render() })

    fun open() {
        if (!controller.isStarted) { controller.start(); startedHere = true }
        last = null; selected = null; notice = null
        controller.addListener(listener)
        controller.scanIfStale()
    }

    fun close() {
        flow.release()
        controller.removeListener(listener)
        if (startedHere) { controller.stop(); startedHere = false }
    }

    /** Opens the connected network's view (details, Reconnect, Forget). */
    fun selectConnected() {
        val c = controller.snapshot.connection
        selected = c.ssid?.takeIf { c.kind == dev.tvlauncher.network.WifiConnection.Kind.WIFI }
        render()
    }

    /** Starts the hidden-network dialog. */
    fun addHidden() = flow.addHidden()

    fun onBack(): Boolean {
        if (flow.onBack()) return true
        val from = selected ?: return false
        selected = null; notice = null
        render(preferKey = "net:$from")
        return true
    }

    fun render(preferKey: String? = null) {
        if (!sink.active || flow.sheetShowing) return
        val s = last ?: controller.snapshot
        val sel = selected?.let { n -> WifiPanelModel.models(s).firstOrNull { !it.hidden && it.ssid == n } }
        if (selected != null && sel == null) selected = null
        if (sel != null) {
            sink.render(WifiPanelModel.detail(sel, s.connection, sel.ssid in s.saved, notice, flow.joining == sel.ssid),
                if (sel.connected) WifiPanelModel.KEY_RECONNECT else WifiPanelModel.KEY_CONNECT)
        } else {
            sink.render(WifiPanelModel.build(s, notice, flow.joining), preferKey)
        }
    }

    fun onRow(row: PanelEntry.Row) {
        when {
            row.key == WifiPanelModel.KEY_TOGGLE -> flow.toggle()
            row.key == WifiPanelModel.KEY_SCAN -> controller.scan()
            row.key == WifiPanelModel.KEY_PERMISSION -> {
                if (SystemPermissions.requestLocation(context)) watchGrant(30)
                else { notice = "Location access can't be asked for right now."; render() }
            }
            row.key == WifiPanelModel.KEY_LOCATION -> TvScreens.open(context, TvScreens.Screen.LOCATION)
            row.key == WifiPanelModel.KEY_HIDDEN -> flow.addHidden()
            row.key == WifiPanelModel.KEY_CONNECT -> selected?.let { flow.connect(it) }
            row.key == WifiPanelModel.KEY_RECONNECT -> flow.reconnect()
            row.key == WifiPanelModel.KEY_FORGET -> selected?.let { if (flow.forget(it)) { selected = null; render() } }
            row.key.startsWith("net:") -> {
                val ssid = row.key.removePrefix("net:")
                if (ssid == "hidden") { flow.addHidden(); return }
                selected = ssid; notice = null
                render()
            }
        }
    }

    /** The permission dialog reports back to the activity, not here: look once a second instead. */
    private fun watchGrant(tries: Int) {
        if (tries <= 0 || !sink.active) return
        sink.anchor.postDelayed({
            if (SystemPermissions.hasLocation(context)) { controller.refresh(); controller.scan() } else watchGrant(tries - 1)
        }, 1000)
    }
}
