package dev.tvlauncher.network

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ui.BouncingDots
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Wi-Fi / Ethernet glass panel. The host calls [show] when the Wi-Fi pill is activated, forwards
 * BACK to [onBack] and restores focus to the pill after the panel closes (the panel does not know
 * the pill). Joining a network is not possible for apps targeting API 29+, so choosing a network
 * opens the TV's own Wi-Fi screen and the panel says so.
 */
class NetworkPanel(
    private val context: Context,
    private val controller: WifiController,
    lite: Boolean
) {
    private val shell = PanelShell(context, lite)
    private var startedHere = false
    private var last: WifiSnapshot? = null
    private var firstRender = true
    private val listener = WifiController.Listener { render(it) }

    val isShowing: Boolean get() = shell.isShowing

    fun show(host: ViewGroup) {
        if (shell.isShowing) return
        if (!controller.isStarted) { controller.start(); startedHere = true }
        last = null
        firstRender = true
        shell.show(host) { }
        controller.addListener(listener)
        controller.scan()
    }

    fun dismiss() {
        if (!shell.isShowing) return
        controller.removeListener(listener)
        if (startedHere) { controller.stop(); startedHere = false }
        shell.dismiss()
    }

    /** Returns true when the key was consumed (the panel was open and is now closing). */
    fun onBack(): Boolean {
        if (!shell.isShowing) return false
        dismiss()
        return true
    }

    private fun render(s: WifiSnapshot) {
        if (!shell.isShowing || s == last) return
        last = s
        val models = WifiListBuilder.build(s.scans, s.connection.bssid, s.connection.ssid)
        val content = NetworkPanelState.wifi(s.hasLocationPermission, s.wifiEnabled, s.scanning, models.size)
        renderHeader(s, models)
        val preferKey = models.firstOrNull()?.let { netKey(it) }
        shell.rebuildBody(if (firstRender) preferKey else null) { buildBody(s, models, content) }
        firstRender = false
    }

    private fun netKey(m: WifiNetworkModel) = "net:" + if (m.hidden) "hidden" else m.ssid

    private fun renderHeader(s: WifiSnapshot, models: List<WifiNetworkModel>) {
        val c = s.connection
        val h = shell.header
        h.removeAllViews()
        val iconRes = when (c.kind) {
            WifiConnection.Kind.ETHERNET -> NetIcons.ETHERNET
            WifiConnection.Kind.WIFI -> NetIcons.forWifi(c.level, true)
            WifiConnection.Kind.NONE -> NetIcons.forWifi(0, false)
        }
        val title = when (c.kind) {
            WifiConnection.Kind.ETHERNET -> "Ethernet connected"
            WifiConnection.Kind.WIFI -> if (c.ssid != null) "Connected to ${c.ssid}" else "Connected to Wi-Fi"
            WifiConnection.Kind.NONE -> "Not connected"
        }
        val sub = when (c.kind) {
            WifiConnection.Kind.ETHERNET -> "Wired network"
            WifiConnection.Kind.WIFI -> if (c.rssi != null) PanelViews.qualityWord(c.level) else "Signal unknown"
            WifiConnection.Kind.NONE -> if (!s.wifiEnabled) "Wi-Fi is off" else "Pick a network below"
        }
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(Theme.TEXT)
        }
        top.addView(icon, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(Theme.S3) })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            style(22f, Theme.medium); text = title; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        texts.addView(TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); text = sub },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(2) })
        top.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (c.kind == WifiConnection.Kind.WIFI) {
            top.addView(SignalBars(context).apply { level = c.level },
                LinearLayout.LayoutParams(context.dp(34), context.dp(28)).apply { marginStart = context.dp(Theme.S3) })
        }
        h.addView(top)

        val chips = ArrayList<String>()
        if (c.kind == WifiConnection.Kind.WIFI) {
            if (c.band != WifiBand.UNKNOWN) chips.add(c.band.label)
            if (c.linkMbps > 0) chips.add("${c.linkMbps} Mbps")
            models.firstOrNull { it.connected }?.let { chips.add(it.security.label) }
        }
        c.ip?.let { chips.add(it) }
        if (chips.isNotEmpty()) {
            val row = LinearLayout(context)
            for (t in chips) row.addView(PanelViews.chip(context, t),
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = context.dp(Theme.S2) })
            h.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(Theme.S3) })
        }
    }

    private fun buildBody(s: WifiSnapshot, models: List<WifiNetworkModel>, content: WifiContent) {
        val body = shell.body
        if (s.hasLocationPermission && s.wifiEnabled) {
            val scan = PanelRow(context, R.drawable.ic_net_refresh, "Scan again", when {
                s.scanning -> "Scanning…"
                s.throttledSeconds > 0 -> "Android limits Wi-Fi scans to a few per two minutes. Try again in ${s.throttledSeconds}s."
                else -> "Refresh the list of networks"
            }, key = "act:scan")
            if (s.scanning) scan.setTrailing(BouncingDots(context, Theme.TEXT_2))
            scan.setOnClickListener { controller.scan() }
            body.addView(scan, rowParams())
        }
        val settings = PanelRow(context, R.drawable.ic_settings, "Open Wi-Fi settings", null, key = "act:settings")
        settings.setOnClickListener { NetworkSettings.openWifi(context) }
        body.addView(settings, rowParams())
        body.addView(PanelViews.note(context, "Joining a network happens in the TV's Wi-Fi screen."))

        when (content) {
            WifiContent.PERMISSION_MISSING -> {
                val card = PanelViews.card(context)
                card.addView(TextView(context).apply {
                    style(Theme.SP_SUB, Theme.medium); text = "Location permission needed to list networks"
                })
                card.addView(PanelViews.note(context, "Android hides Wi-Fi scan results from apps without it. Run this once from a computer, then reopen this panel:"))
                card.addView(PanelViews.note(context, NetworkSettings.ADB_LOCATION_GRANT, mono = true))
                card.addView(PanelViews.note(context, "Your connection details above work without it."))
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S3) })
            }
            WifiContent.WIFI_OFF ->
                body.addView(PanelViews.note(context, "Wi-Fi is off. Turn it on in the TV's Wi-Fi settings."))
            WifiContent.SCANNING -> body.addView(PanelViews.centered(context,
                BouncingDots(context, Theme.TEXT_2),
                TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); text = "Looking for networks…" }))
            WifiContent.EMPTY -> body.addView(PanelViews.note(context, "No networks found. Try Scan again."))
            WifiContent.LIST -> {
                body.addView(PanelViews.sectionLabel(context, "Nearby networks"))
                for (m in models) body.addView(networkRow(m), rowParams())
            }
        }
    }

    private fun networkRow(m: WifiNetworkModel): PanelRow {
        val parts = ArrayList<String>()
        if (m.connected) parts.add("Connected")
        if (m.band != WifiBand.UNKNOWN) parts.add(m.band.label)
        parts.add(m.security.label)
        val row = PanelRow(context, NetIcons.forWifi(m.signalLevel, true), m.ssid, parts.joinToString(" · "), key = netKey(m))
        when {
            m.connected -> row.trailingIcon(R.drawable.ic_net_check)
            !m.security.isOpen -> row.trailingIcon(R.drawable.ic_net_lock, 18)
        }
        row.setOnClickListener { NetworkSettings.openWifi(context) }
        return row
    }

    private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(2) }
}
