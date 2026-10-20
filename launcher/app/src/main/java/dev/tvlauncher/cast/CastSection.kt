package dev.tvlauncher.cast

import android.content.Context
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.Chip
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.devices.DevicesPanel
import dev.tvlauncher.discover.DiscoverActions
import dev.tvlauncher.discover.DiscoverItem
import dev.tvlauncher.discover.PlayStoreLinks
import dev.tvlauncher.discover.PopularApps
import dev.tvlauncher.discover.StoreLauncher

/**
 * Settings > Connections > Cast & AirPlay: the TV's name as phones see it, which receivers the
 * TV has (Google Cast built in, the maker's AirPlay input) and how to use them. Read-only apart
 * from opening the AirPlay input or a receiver app.
 */
object CastSection : SettingsSection, SpecifiedPage {
    const val ID = "cast_airplay"
    override val id = ID
    override val title = "Cast & AirPlay"
    override val iconRes = R.drawable.ic_dev_phonecast
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Show your phone on the TV: AirPlay, Chromecast and screen mirroring."
    override val spec get() = CastModel.SPEC

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val page = SettingsPage(context, nav.style, spec)
        val state = CastProbe.state(app)
        for (r in CastModel.rows(state)) {
            val icon = when (r.key) {
                CastModel.NAME -> R.drawable.ic_sys_tv
                CastModel.NETWORK -> R.drawable.ic_net_wifi_4
                CastModel.HOWTO -> R.drawable.ic_net_kind_phone
                else -> R.drawable.ic_dev_phonecast
            }
            val kind = when (r.kind) {
                CastModel.Kind.INFO -> if (r.positive) RowKind.Action(r.value, chevron = false, chip = Chip.POSITIVE) {} else RowKind.Info(r.value)
                CastModel.Kind.ACTION -> RowKind.Action(r.value.ifEmpty { null }) { onAction(app, nav, r.key, state) }
            }
            page.row(r.key, r.title, kind, subtitle = r.subtitle, iconRes = icon)
        }
        return page
    }

    private fun onAction(app: Context, nav: SettingsNav, key: String, state: CastModel.State) {
        when (key) {
            CastModel.OPEN_AIRPLAY -> {
                val id = state.airPlayInput ?: CastProbe.airPlayInput(app)
                if (id == null || !DevicesPanel.switchTo(app, id)) nav.notify("Couldn't open AirPlay")
            }
            CastModel.GET_RECEIVER -> openReceiverApp(app, nav)
            CastModel.HOWTO -> nav.push("How to cast") { c, n -> howTo(c, n) }
        }
    }

    /** Opens Kodi, else its store page the same way Discover does. */
    private fun openReceiverApp(app: Context, nav: SettingsNav) {
        val pkg = CastModel.RECEIVER_APP_PACKAGE
        val installed = CastProbe.installed(app, pkg)
        val entry = PopularApps.byPackage(pkg)
        val msg = if (entry != null) {
            val action = DiscoverActions.decide(entry, if (installed) setOf(pkg) else emptySet(), StoreLauncher.isGetAppsInstalled(app))
            StoreLauncher.perform(app, DiscoverItem(entry, action))
        } else if (installed) {
            if (StoreLauncher.openApp(app, pkg)) null else "Couldn't open ${CastModel.RECEIVER_APP_NAME}"
        } else {
            if (StoreLauncher.openPlayStore(app, pkg)) null else PlayStoreLinks.UNAVAILABLE_TEXT
        }
        msg?.let { nav.notify(it) }
    }

    private fun howTo(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        for ((heading, text) in CastModel.howTo(CastProbe.state(context))) {
            page.group(heading)
            page.note(text)
        }
        page.group("Network")
        page.note(CastModel.NETWORK_HOWTO)
        return page
    }
}
