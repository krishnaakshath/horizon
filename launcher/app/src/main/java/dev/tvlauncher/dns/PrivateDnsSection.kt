package dev.tvlauncher.dns

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.Chip
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.discover.DiscoverActions
import dev.tvlauncher.discover.DiscoverItem
import dev.tvlauncher.discover.PopularApps
import dev.tvlauncher.discover.StoreLauncher

/**
 * The TV's Private DNS settings and network state. Reading needs nothing; writing needs
 * WRITE_SECURE_SETTINGS, which only the one-time setup grants. The launcher has no INTERNET
 * permission: the "Check" row reads what Android itself reports about the network.
 */
object PrivateDnsSystem {
    const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
    private const val KEY_DEFAULT_MODE = "private_dns_default_mode"

    fun canWrite(context: Context): Boolean = try {
        context.checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private fun global(context: Context, key: String): String? = try {
        Settings.Global.getString(context.contentResolver, key)
    } catch (e: Throwable) {
        null
    }

    fun rawMode(context: Context): String? = global(context, PrivateDns.KEY_MODE)

    fun rawSpecifier(context: Context): String? = global(context, PrivateDns.KEY_SPECIFIER)

    fun current(context: Context): PrivateDns.Current =
        PrivateDns.parse(rawMode(context), rawSpecifier(context), global(context, KEY_DEFAULT_MODE))

    /** Applies [writes] in order; false (and stops) at the first refusal. */
    fun apply(context: Context, writes: List<Pair<String, String?>>): Boolean {
        if (!canWrite(context)) return false
        return try {
            writes.all { (k, v) -> Settings.Global.putString(context.contentResolver, k, v) }
        } catch (e: Throwable) {
            false
        }
    }

    data class Net(val hasNetwork: Boolean, val validated: Boolean, val active: Boolean, val serverName: String?, val vpn: Boolean)

    fun net(context: Context): Net = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        if (n == null) Net(false, false, false, null, false) else {
            val caps = cm.getNetworkCapabilities(n)
            val lp = cm.getLinkProperties(n)
            Net(
                hasNetwork = true,
                validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                active = Build.VERSION.SDK_INT >= 28 && lp?.isPrivateDnsActive == true,
                serverName = if (Build.VERSION.SDK_INT >= 28) lp?.privateDnsServerName else null,
                vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            )
        }
    } catch (e: Throwable) {
        Net(false, false, false, null, false)
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: Throwable) {
        false
    }
}

/** Settings > Connections > Private DNS. */
object PrivateDnsSection : SettingsSection, SpecifiedPage {
    const val ID = "private_dns"
    override val id = ID
    override val title = "Private DNS"
    override val iconRes = R.drawable.ic_set_shield
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Encrypted, faster name lookups for the whole TV."
    override val spec = PageSpec(ID, listOf("dns.current", "dns.check", "dns.warp"))

    val PICKER_SPEC = PageSpec("private_dns.choose", PrivateDns.Choice.values().map { "dns.opt.${it.name.lowercase()}" })

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val page = SettingsPage(context, nav.style, spec)
        val canWrite = PrivateDnsSystem.canWrite(app)
        val current = PrivateDnsSystem.current(app)

        page.row("dns.current", "Private DNS", if (canWrite) RowKind.Action(current.label) {
            nav.push("Private DNS") { c, n -> picker(c, n) }
        } else RowKind.Info(current.label), subtitle = PrivateDns.chooseSubtitle(canWrite), iconRes = R.drawable.ic_set_shield)

        lateinit var check: dev.tvlauncher.appsettings.SettingsRow
        val refreshCheck = {
            check.setValue("Checking…")
            nav.background({ PrivateDnsSystem.net(app) to PrivateDnsSystem.current(app) }) { (net, cur) ->
                check.setValue(DnsCheck.status(cur, net.hasNetwork, net.active, net.serverName, net.validated))
            }
        }
        check = page.row("dns.check", "Check connection", RowKind.Action("…", chevron = false) { refreshCheck() },
            subtitle = "What the TV reports for this network", iconRes = R.drawable.ic_net_check)
        refreshCheck()

        val warpInstalled = PrivateDnsSystem.isInstalled(app, Warp.PACKAGE)
        val vpn = PrivateDnsSystem.net(app).vpn
        page.row("dns.warp", Warp.TITLE, RowKind.Action(Warp.value(warpInstalled),
            chip = if (warpInstalled) Chip.POSITIVE else Chip.NEUTRAL) {
            openWarp(app, nav)
        }, subtitle = Warp.subtitle(warpInstalled, vpn), iconRes = R.drawable.ic_net_lock)
        return page
    }

    /** Open the app, else its store page the same way Discover does (Play detail page). */
    private fun openWarp(app: Context, nav: SettingsNav) {
        val entry = PopularApps.byPackage(Warp.PACKAGE)
        val installed = PrivateDnsSystem.isInstalled(app, Warp.PACKAGE)
        val msg = if (entry != null) {
            val action = DiscoverActions.decide(entry, if (installed) setOf(Warp.PACKAGE) else emptySet(), StoreLauncher.isGetAppsInstalled(app))
            StoreLauncher.perform(app, DiscoverItem(entry, action))
        } else if (installed) {
            if (StoreLauncher.openApp(app, Warp.PACKAGE)) null else "Couldn't open ${Warp.TITLE}"
        } else {
            if (StoreLauncher.openPlayStore(app, Warp.PACKAGE)) null else dev.tvlauncher.discover.PlayStoreLinks.UNAVAILABLE_TEXT
        }
        msg?.let { nav.notify(it) }
    }

    private fun picker(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val page = SettingsPage(context, nav.style, PICKER_SPEC)
        val current = PrivateDnsSystem.current(app)
        page.note(PrivateDns.EXPLAIN + ". If a choice can't be reached on this network, the previous one comes back.")
        for (c in PrivateDns.Choice.values()) {
            val key = "dns.opt.${c.name.lowercase()}"
            page.row(key, c.label, RowKind.Action(PrivateDns.marker(c, current), chevron = false) {
                choose(app, nav, c)
            }, subtitle = c.host?.let { "${c.note} · $it" } ?: c.note,
                iconRes = if (current.choice == c) R.drawable.ic_set_check else 0)
            if (current.choice == c) page.preferredFocus = key
        }
        return page
    }

    private fun choose(app: Context, nav: SettingsNav, c: PrivateDns.Choice) {
        val prevMode = PrivateDnsSystem.rawMode(app)
        val prevSpec = PrivateDnsSystem.rawSpecifier(app)
        val before = PrivateDnsSystem.net(app)
        if (!PrivateDnsSystem.apply(app, PrivateDns.writes(c))) {
            nav.notify(PrivateDns.NEEDS_SETUP)
            return
        }
        nav.notify(if (c.mode == PrivateDns.MODE_HOSTNAME) "Switching to ${c.label}…" else "${c.label} saved")
        nav.refresh()
        if (c.mode != PrivateDns.MODE_HOSTNAME) return
        nav.task({
            val start = SystemClock.elapsedRealtime()
            var outcome: DnsCheck.Outcome
            while (true) {
                SystemClock.sleep(DnsCheck.POLL_MS)
                val n = PrivateDnsSystem.net(app)
                outcome = DnsCheck.decide(c, n.hasNetwork, before.validated, n.serverName, n.validated, SystemClock.elapsedRealtime() - start)
                if (outcome != DnsCheck.Outcome.WAIT) break
            }
            if (outcome == DnsCheck.Outcome.REVERT) PrivateDnsSystem.apply(app, PrivateDns.restore(prevMode, prevSpec))
            outcome
        }) { outcome ->
            nav.notify(DnsCheck.message(c, outcome))
            nav.refreshCurrent()
        }
    }
}
