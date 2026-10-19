package dev.tvlauncher.appsettings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.ui.SystemActions

/** Shortcuts into the TV's own Settings pages, with cheap live status where available. */
object NetworkSection : SettingsSection {
    const val ID = "network"
    override val id = ID
    override val title = "Network & system"
    override val iconRes = R.drawable.ic_set_network

    private class Shortcut(val key: String, val title: String, val action: String, val icon: Int)

    private val connections = listOf(
        Shortcut("wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS, R.drawable.ic_sc_wifi),
        Shortcut("network", "Network", Settings.ACTION_WIRELESS_SETTINGS, R.drawable.ic_set_network),
        Shortcut("bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, R.drawable.ic_sc_bluetooth)
    )
    private val device = listOf(
        Shortcut("display", "Display", Settings.ACTION_DISPLAY_SETTINGS, R.drawable.ic_sc_display),
        Shortcut("sound", "Sound", Settings.ACTION_SOUND_SETTINGS, R.drawable.ic_set_sounds),
        Shortcut("apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS, R.drawable.ic_sc_apps),
        Shortcut("storage", "Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, R.drawable.ic_sc_storage),
        Shortcut("date", "Date & time", Settings.ACTION_DATE_SETTINGS, R.drawable.ic_sc_date),
        Shortcut("device", "Device info", Settings.ACTION_DEVICE_INFO_SETTINGS, R.drawable.ic_sc_device)
    )
    private val advanced = listOf(
        Shortcut("developer", "Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, R.drawable.ic_sc_developer),
        Shortcut("all", "All system settings", Settings.ACTION_SETTINGS, R.drawable.ic_sc_settings)
    )

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.lead("These open the TV's own Settings pages. Press BACK there to return here.")
        val rows = HashMap<String, SettingsRow>()
        fun group(label: String, list: List<Shortcut>) {
            page.group(label)
            for (s in list) {
                rows[s.key] = page.row("network.${s.key}", s.title, RowKind.Action {
                    if (!SystemActions.start(context, Intent(s.action))) {
                        nav.notify("This TV has no ${s.title} page")
                    }
                }, subtitle = "Opens the TV's ${s.title} page", iconRes = s.icon)
            }
        }
        group("Connections", connections)
        group("Device", device)
        group("Advanced", advanced)
        nav.background({ NetworkStatusReader.read(context) }) { st ->
            rows["wifi"]?.setValue(st.wifi)
            rows["bluetooth"]?.setValue(st.bluetooth)
            rows["storage"]?.setValue(st.storage)
        }
        return page
    }
}
