package dev.tvlauncher.system

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import dev.tvlauncher.R
import dev.tvlauncher.network.WifiConnection
import dev.tvlauncher.network.WifiController
import dev.tvlauncher.network.WifiPanelModel
import dev.tvlauncher.network.WifiSecurity

/**
 * The launcher's Wi-Fi actions with their dialogs, shared by the home Wi-Fi panel and Settings >
 * Wi-Fi: switch, join (password typed with the TV keyboard in a [TextEntrySheet]), rejoin saved
 * networks, hidden networks, Reconnect and Forget. Results come back as one friendly line through
 * [onNotice]; [onChanged] asks the host to redraw. [host] is where the sheet is shown.
 */
class WifiFlow(
    private val context: Context,
    private val controller: WifiController,
    lite: Boolean,
    private val host: () -> ViewGroup?,
    private val onNotice: (String?) -> Unit,
    private val onChanged: () -> Unit
) {
    private class Typed(val ssid: String, val security: WifiSecurity, val hidden: Boolean)

    private val actions = WifiActions(context)
    private val sheet = TextEntrySheet(context, lite)
    private var typed: Typed? = null
    private val join = WifiJoinSession(
        context,
        currentSsid = { controller.snapshot.connection.takeIf { it.kind == WifiConnection.Kind.WIFI }?.ssid },
        inRange = { ssid -> controller.snapshot.scans.any { it.ssid == ssid } },
        onOutcome = { ssid, o -> onOutcome(ssid, o) }
    )

    init {
        sheet.onClosed = { onChanged() }
    }

    /** The SSID being joined right now, if any. */
    val joining: String? get() = join.target
    val sheetShowing: Boolean get() = sheet.isShowing

    /** BACK: closes the sheet first. */
    fun onBack(): Boolean {
        if (!sheet.isShowing) return false
        sheet.dismiss(); return true
    }

    fun release() {
        sheet.dismiss()
        join.cancel()
        typed = null
    }

    fun toggle() {
        val on = controller.snapshot.wifiEnabled
        onNotice(if (actions.setEnabled(!on)) null else "This TV didn't let the launcher switch Wi-Fi.")
        onChanged()
    }

    fun reconnect() {
        onNotice(if (actions.reconnect()) "Reconnecting…" else "Couldn't reconnect. Try again.")
        onChanged()
    }

    /** Joins [ssid]: saved networks directly, open ones at once, secured ones after the password. */
    fun connect(ssid: String) {
        val s = controller.snapshot
        val m = WifiPanelModel.models(s).firstOrNull { !it.hidden && it.ssid == ssid } ?: return
        onNotice(null)
        val savedId = s.saved[ssid]
        when {
            savedId != null -> { typed = null; start(ssid) { actions.connect(savedId) == WifiActions.Result.OK } }
            m.security.isOpen -> joinWith(ssid, m.security, "", hidden = false)
            !WifiCredentials.supported(m.security, Build.VERSION.SDK_INT) -> {
                onNotice((WifiCredentials.build(ssid, m.security, "", Build.VERSION.SDK_INT) as? CredentialResult.Invalid)?.message)
                onChanged()
            }
            else -> askPassword(ssid, m.security, hidden = false)
        }
    }

    /** Hidden network: its name, then its password (empty = open). */
    fun addHidden() {
        val h = host() ?: return
        if (!controller.snapshot.wifiEnabled) { onNotice("Turn Wi-Fi on first."); onChanged(); return }
        sheet.show(h, TextEntrySheet.Request(
            title = "Hidden network",
            subtitle = "Type the network name exactly",
            kind = TextEntrySheet.Kind.TEXT,
            confirm = "Next",
            hint = "Network name",
            icon = R.drawable.ic_sc_wifi,
            onCancel = onChanged
        ) { name ->
            val n = name.trim()
            if (n.isEmpty()) return@Request "Type the network name."
            sheet.show(h, TextEntrySheet.Request(n, "Password (leave empty if the network is open)",
                TextEntrySheet.Kind.PASSWORD, "Connect", hint = "Password", onCancel = onChanged) { pw ->
                joinWith(n, if (pw.isEmpty()) WifiSecurity.OPEN else WifiSecurity.WPA2, pw, hidden = true)
            })
            null
        })
    }

    /** Forgets [ssid]; true when it is gone. */
    fun forget(ssid: String): Boolean {
        val id = controller.snapshot.saved[ssid] ?: return false
        val ok = actions.forget(id) == WifiActions.Result.OK
        onNotice(if (ok) "$ssid forgotten" else "$ssid was saved by the TV itself, so only the TV's settings can forget it.")
        controller.refresh()
        onChanged()
        return ok
    }

    private fun askPassword(ssid: String, security: WifiSecurity, hidden: Boolean, error: String? = null) {
        val h = host() ?: return
        sheet.show(h, TextEntrySheet.Request(
            title = ssid,
            subtitle = "Enter the Wi-Fi password",
            kind = TextEntrySheet.Kind.PASSWORD,
            confirm = "Connect",
            hint = "Password",
            onCancel = onChanged
        ) { pw -> joinWith(ssid, security, pw, hidden) })
        error?.let { sheet.showError(it) }
    }

    /** Validates first; returns a line for the sheet, or null when the attempt started. */
    private fun joinWith(ssid: String, security: WifiSecurity, password: String, hidden: Boolean): String? =
        when (val r = WifiCredentials.build(ssid, security, password, Build.VERSION.SDK_INT, hidden)) {
            is CredentialResult.Invalid -> r.message
            is CredentialResult.Ok -> {
                typed = if (password.isNotEmpty()) Typed(ssid, security, hidden) else null
                start(ssid) { actions.join(r.credential) == WifiActions.Result.OK }
                null
            }
        }

    private inline fun start(ssid: String, attempt: () -> Boolean) {
        join.start(ssid)
        onNotice("Connecting to $ssid…")
        if (!attempt()) join.refused()
        onChanged()
    }

    private fun onOutcome(ssid: String, o: JoinOutcome) {
        onNotice(if (o == JoinOutcome.CONNECTED) "Connected to $ssid" else o.message)
        controller.refresh()
        onChanged()
        val t = typed
        if (o == JoinOutcome.WRONG_PASSWORD && t != null && t.ssid == ssid) askPassword(ssid, t.security, t.hidden, "Wrong password")
        if (o.finished) typed = null
    }
}
