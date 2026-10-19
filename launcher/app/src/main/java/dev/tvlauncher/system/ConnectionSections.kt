package dev.tvlauncher.system

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.devices.DeviceMonitor
import dev.tvlauncher.devices.DevicesPanel
import dev.tvlauncher.devices.InputsModel
import dev.tvlauncher.network.BluetoothController
import dev.tvlauncher.network.NetIcons
import dev.tvlauncher.network.PanelText
import dev.tvlauncher.network.WifiController
import dev.tvlauncher.network.WifiNetworkModel
import dev.tvlauncher.network.WifiBand

internal fun SettingsNav.lite() = profile.effectsMode == EffectsMode.LITE
internal fun contentRoot(context: Context): ViewGroup? = (context as? Activity)?.findViewById(android.R.id.content)

/** Settings > Wi-Fi & network: everything in the launcher (switch, join, forget, Ethernet, a network check). */
object WifiSettingsSection : SettingsSection, SpecifiedPage {
    override val id = SystemSpecs.NETWORK
    override val title = "Wi-Fi & network"
    override val iconRes = R.drawable.ic_sc_wifi
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Join, switch and check networks right here."
    override val spec = SystemSpecs.WIFI

    private enum class Mode { LIST, CURRENT, HIDDEN }

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val actions = WifiActions(context)
        val on = actions.isEnabled()
        page.row("wifi.toggle", "Wi-Fi", RowKind.Toggle(on) { want ->
            if (!actions.setEnabled(want)) nav.notify("This TV didn't let the launcher switch Wi-Fi.")
            page.postDelayed({ nav.refresh() }, 1500)
        }, iconRes = R.drawable.ic_sc_wifi)
        val current = page.row("wifi.current", "Connection", RowKind.Action("…") {
            nav.push("Connection") { c, n -> screenPage(c, n, Mode.CURRENT) }
        }, iconRes = R.drawable.ic_set_network)
        page.row("wifi.choose", "Choose a network", RowKind.Action {
            nav.push("Choose a network") { c, n -> screenPage(c, n, Mode.LIST) }
        }, subtitle = "Nearby and saved networks", iconRes = R.drawable.ic_net_wifi_4)
        page.row("wifi.hidden", "Add hidden network", RowKind.Action {
            nav.push("Hidden network") { c, n -> screenPage(c, n, Mode.HIDDEN) }
        }, iconRes = R.drawable.ic_net_lock)
        val eth = page.row("net.ethernet", "Ethernet", RowKind.Info("…"), iconRes = R.drawable.ic_net_ethernet)
        page.row("net.check", "Network check", RowKind.Action {
            nav.push("Network check") { c, n -> diagnostics(c, n) }
        }, subtitle = "Internet, address, gateway and DNS", iconRes = R.drawable.ic_net_refresh)
        page.advanced(nav, "network.advanced", "Location") { adv ->
            adv.row("wifi.location", "Location", RowKind.Action("TV settings") {
                if (!TvScreens.open(context, TvScreens.Screen.LOCATION)) nav.notify("This TV has no Location screen")
            }, subtitle = "Needed to list networks; only the TV can switch it", iconRes = R.drawable.ic_settings)
        }
        nav.background({ NetInfo.read(context) }) { n ->
            current.setValue(when {
                n.wifi -> n.ssid ?: "Connected"
                n.ethernet -> "Ethernet"
                !n.wifiEnabled -> "Wi-Fi off"
                else -> "Not connected"
            })
            eth.setValue(if (n.ethernet) n.ip ?: "Connected" else "Not connected")
        }
        return page
    }

    private fun screenPage(context: Context, nav: SettingsNav, mode: Mode): View {
        val rows = PanelRowsPage(context, nav.style)
        val controller = WifiController(context)
        val screen = WifiScreen(context, controller, nav.lite(), rows) { contentRoot(context) }
        rows.onRowClick = screen::onRow
        rows.onBackKey = screen::onBack
        rows.onShown = {
            screen.open()
            when (mode) {
                Mode.CURRENT -> screen.selectConnected()
                Mode.HIDDEN -> screen.addHidden()
                Mode.LIST -> Unit
            }
        }
        rows.onHidden = { screen.close() }
        return rows.view
    }

    private fun diagnostics(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, SystemSpecs.DIAGNOSTICS)
        val link = page.row("diag.link", "Connection", RowKind.Info("…"), iconRes = R.drawable.ic_set_network)
        val internet = page.row("diag.internet", "Internet", RowKind.Info("…"), iconRes = R.drawable.ic_sc_wifi)
        val ip = page.row("diag.ip", "IP address", RowKind.Info("…"), iconRes = R.drawable.ic_sc_device)
        val gw = page.row("diag.gateway", "Router", RowKind.Info("…"), iconRes = R.drawable.ic_sc_device)
        val dns = page.row("diag.dns", "DNS", RowKind.Info("…"), iconRes = R.drawable.ic_sc_device)
        val speed = page.row("diag.speed", "Link speed", RowKind.Info("…"), iconRes = R.drawable.ic_net_refresh)
        nav.background({ NetInfo.read(context) }) { n ->
            link.setValue(when {
                n.wifi -> listOfNotNull(n.ssid ?: "Wi-Fi", n.rssi?.let { PanelText.signalWord(WifiNetworkModel.levelOf(it)) },
                    WifiNetworkModel.bandOf(n.frequencyMhz).takeIf { it != WifiBand.UNKNOWN }?.label).joinToString(" · ")
                n.ethernet -> "Ethernet"
                else -> "None"
            })
            internet.setValue(SystemText.internet(n.hasNetwork, n.validated, n.captive))
            ip.setValue(n.ip ?: "None")
            gw.setValue(n.gateway ?: "None")
            dns.setValue(n.dns.take(2).joinToString(", ").ifEmpty { "None" })
            speed.setValue(if (n.wifi) SystemText.linkSpeed(n.linkMbps) else if (n.ethernet) "Wired" else "None")
        }
        return page
    }
}

/** Settings > Bluetooth: switch, then the device list (pair, connect, disconnect, forget) in the launcher. */
object BluetoothSettingsSection : SettingsSection, SpecifiedPage {
    override val id = SystemSpecs.BLUETOOTH
    override val title = "Bluetooth"
    override val iconRes = R.drawable.ic_sc_bluetooth
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Pair, connect and forget remotes, speakers and headphones."
    override val spec = SystemSpecs.BT

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val c = BluetoothController(context)
        val s = c.snapshot
        if (!s.present) {
            page.row("bt.toggle", "Bluetooth", RowKind.Info("Not on this TV"), iconRes = R.drawable.ic_sc_bluetooth)
            return page
        }
        page.row("bt.toggle", "Bluetooth", RowKind.Toggle(s.enabled) { want ->
            if (c.setEnabled(want) != BluetoothController.Result.OK) nav.notify("This TV didn't let the launcher switch Bluetooth.")
            page.postDelayed({ nav.refresh() }, 1500)
        }, iconRes = R.drawable.ic_sc_bluetooth)
        page.row("bt.open", "Devices", RowKind.Action(if (s.enabled) "Pair or connect" else null) {
            nav.push("Bluetooth devices") { ctx, n -> devicesPage(ctx, n) }
        }, subtitle = "Search, pair, connect and forget", iconRes = NetIcons.forBluetooth(NetIcons.BtState.ON))
        val paired = s.sections.paired.size + s.sections.connected.size
        page.row("bt.paired", "Paired", RowKind.Info(if (paired == 1) "1 device" else "$paired devices"),
            subtitle = s.sections.connected.firstOrNull()?.let { "Connected: ${it.name}" }, iconRes = R.drawable.ic_set_devices)
        return page
    }

    private fun devicesPage(context: Context, nav: SettingsNav): View {
        val rows = PanelRowsPage(context, nav.style)
        val screen = BluetoothScreen(context, BluetoothController(context), rows)
        rows.onRowClick = screen::onRow
        rows.onBackKey = screen::onBack
        rows.onShown = { screen.open() }
        rows.onHidden = { screen.close() }
        return rows.view
    }
}

/**
 * Settings > Devices as native rows in groups (Inputs, Controllers & remotes, Audio, Storage,
 * Other, Network) from [DevicesPageModel]; OK on an input switches to it, Network opens Wi-Fi.
 */
object DevicesSettingsSection : SettingsSection {
    override val id = "devices"
    override val title = "Devices"
    override val iconRes = R.drawable.ic_set_devices
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Inputs, remotes and everything plugged in."

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val monitor = DeviceMonitor(context) { }
        var shown: List<DevicesPageModel.Section>? = null
        fun fill() {
            val names = monitor.devices().mapNotNull { d ->
                dev.tvlauncher.devices.DeviceActions.passthroughInputId(d)?.let { it to d.name }
            }.toMap()
            val sections = DevicesPageModel.sections(monitor.devices(), InputsModel.merge(InputReader.read(context), names))
            if (sections == shown) return
            shown = sections
            val focused = (page.findFocus() as? View)?.let { f ->
                generateSequence(f) { it.parent as? View }.firstOrNull { it is dev.tvlauncher.appsettings.SettingsRow }?.tag as? String
            }
            page.column.removeAllViews()
            for (sec in sections) {
                page.group(sec.title)
                for (r in sec.rows) {
                    val kind = when (r.action) {
                        DevicesPageModel.Action.SWITCH_INPUT -> RowKind.Action(r.value, chevron = true) {
                            if (!DevicesPanel.switchTo(context, r.target ?: return@Action)) nav.notify("Couldn't switch to ${r.title}.")
                        }
                        DevicesPageModel.Action.NETWORK -> RowKind.Action(r.value, chevron = true) {
                            nav.push(WifiSettingsSection.title) { c, n -> WifiSettingsSection.buildPage(c, n) }
                        }
                        DevicesPageModel.Action.NONE -> RowKind.Info(r.value.orEmpty())
                    }
                    page.body.row(r.key, r.title, kind, iconRes = r.icon)
                }
            }
            focused?.let { k -> page.findViewWithTag<View>(k)?.requestFocus() }
            nav.contentReady()
        }
        page.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                monitor.onChanged = { fill() }
                monitor.start()
                fill()
            }

            override fun onViewDetachedFromWindow(v: View) {
                monitor.onChanged = null
                monitor.stop()
            }
        })
        return page
    }
}
