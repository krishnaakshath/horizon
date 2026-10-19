package dev.tvlauncher.appsettings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.ui.SystemActions

/** A row that opens one of the TV's own Settings pages. */
internal class Shortcut(val key: String, val title: String, val action: String, val icon: Int, val subtitle: String? = null)

internal fun SettingsPage.Block.shortcut(context: Context, nav: SettingsNav, prefix: String, s: Shortcut): SettingsRow =
    row("$prefix.${s.key}", s.title, RowKind.Action {
        if (!SystemActions.start(context, Intent(s.action))) nav.notify("This TV has no ${s.title} page")
    }, subtitle = s.subtitle, iconRes = s.icon)

/** Wi-Fi status and the TV's network pages. */
object NetworkSection : SettingsSection, SpecifiedPage {
    const val ID = "network"
    override val id = ID
    override val title = "Wi-Fi & network"
    override val iconRes = R.drawable.ic_sc_wifi
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Opens the TV's own network pages. BACK returns here."
    override val spec = PageSpec(ID, listOf("network.wifi", "network.network"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val b = page.body
        val wifi = b.shortcut(context, nav, "network", Shortcut("wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS, R.drawable.ic_sc_wifi))
        b.shortcut(context, nav, "network", Shortcut("network", "Network & internet", Settings.ACTION_WIRELESS_SETTINGS,
            R.drawable.ic_set_network, "Ethernet, proxy and IP settings"))
        nav.background({ NetworkStatusReader.read(context) }) { st -> wifi.setValue(st.wifi) }
        return page
    }
}

/** Bluetooth status and the TV's pairing page. */
object BluetoothSection : SettingsSection, SpecifiedPage {
    const val ID = "bluetooth"
    override val id = ID
    override val title = "Bluetooth"
    override val iconRes = R.drawable.ic_sc_bluetooth
    override val group = SettingsGroup.CONNECTIONS
    override val description = "Remotes, speakers and headphones pair in the TV's own settings."
    override val spec = PageSpec(ID, listOf("bluetooth.settings"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val row = page.body.shortcut(context, nav, "bluetooth",
            Shortcut("settings", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, R.drawable.ic_sc_bluetooth, "Pair and manage devices"))
        nav.background({ NetworkStatusReader.read(context) }) { st -> row.setValue(st.bluetooth) }
        return page
    }
}

/** Shortcuts into the TV's own Settings pages; rarely used ones under Advanced. */
object SystemSection : SettingsSection, SpecifiedPage {
    const val ID = "system"
    override val id = ID
    override val title = "System shortcuts"
    override val iconRes = R.drawable.ic_sc_settings
    override val group = SettingsGroup.SYSTEM
    override val description = "Opens the TV's own Settings pages. BACK returns here."

    private val main = listOf(
        Shortcut("display", "Display", Settings.ACTION_DISPLAY_SETTINGS, R.drawable.ic_sc_display),
        Shortcut("sound", "Sound", Settings.ACTION_SOUND_SETTINGS, R.drawable.ic_set_sounds, "TV speakers and audio output"),
        Shortcut("apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS, R.drawable.ic_sc_apps),
        Shortcut("storage", "Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, R.drawable.ic_sc_storage),
        Shortcut("date", "Date & time", Settings.ACTION_DATE_SETTINGS, R.drawable.ic_sc_date),
        Shortcut("device", "Device info", Settings.ACTION_DEVICE_INFO_SETTINGS, R.drawable.ic_sc_device)
    )
    private val advanced = listOf(
        Shortcut("developer", "Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, R.drawable.ic_sc_developer),
        Shortcut("all", "All system settings", Settings.ACTION_SETTINGS, R.drawable.ic_sc_settings)
    )

    override val spec = PageSpec(ID, main.map { "system.${it.key}" }, advanced.map { "system.${it.key}" })

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val b = page.body
        val rows = main.associate { it.key to b.shortcut(context, nav, ID, it) }
        page.advanced(nav, "system.advanced", "Developer options, all system settings") { adv ->
            advanced.forEach { adv.shortcut(context, nav, ID, it) }
        }
        nav.background({ NetworkStatusReader.read(context) }) { st -> rows["storage"]?.setValue(st.storage) }
        return page
    }
}
