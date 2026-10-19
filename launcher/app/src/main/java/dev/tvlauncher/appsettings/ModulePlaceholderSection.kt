package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import dev.tvlauncher.R

/**
 * Sidebar slot for a feature that ships in its own module. The module replaces this entry by
 * calling [SettingsRegistry.register] with a section of the same [id]. Until then the page says
 * the module is coming (when [probe] finds nothing) or that it is installed but not registered.
 */
class ModulePlaceholderSection(
    override val id: String,
    override val title: String,
    override val iconRes: Int,
    private val probe: Probe,
    private val blurb: String
) : SettingsSection {
    /** How a module is detected: a class compiled into this APK, or a Java/Android package name. */
    sealed class Probe {
        abstract fun isPresent(context: Context): Boolean

        data class ClassName(val name: String) : Probe() {
            override fun isPresent(context: Context) = ModuleProbe.isPresent(name)
        }

        /** Present if any class was loaded under [name] or an installed app has that package name. */
        data class PackageName(val name: String) : Probe() {
            override fun isPresent(context: Context) =
                ModuleProbe.isPackageLoaded(name) || ModuleProbe.isAppInstalled(context, name)
        }
    }

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.lead(blurb)
        if (!probe.isPresent(context)) {
            page.row("$id.status", "Coming from the $title module", RowKind.Info(),
                subtitle = "This build does not include it yet.", iconRes = iconRes)
        } else {
            page.row("$id.status", "$title module installed", RowKind.Info(),
                subtitle = "Its settings appear here once it registers its page.", iconRes = iconRes)
        }
        return page
    }

    companion object {
        const val SOUNDS = "sounds"
        const val INSIGHTS = "insights"
        const val HELPER = "helper"
        const val BROWSER = "browser"
        const val DEVICES = "devices"

        /** What is probed (never instantiated) to tell "coming" from "installed". */
        val SOUNDS_PROBE = Probe.ClassName("dev.tvlauncher.ui.SoundSettingsPanel")
        val INSIGHTS_PROBE = Probe.ClassName("dev.tvlauncher.insights.InsightsActivity")
        val HELPER_PROBE = Probe.PackageName("dev.tvlauncher.helper")
        val BROWSER_PROBE = Probe.PackageName("dev.tvlauncher.browser")
        val DEVICES_PROBE = Probe.ClassName("dev.tvlauncher.devices.DevicesPanel")

        fun sounds() = ModulePlaceholderSection(SOUNDS, "Sounds", R.drawable.ic_set_sounds, SOUNDS_PROBE,
            "Navigation and launch sounds for the home screen.")

        fun insights() = ModulePlaceholderSection(INSIGHTS, "Insights", R.drawable.ic_set_insights, INSIGHTS_PROBE,
            "What you watch most and how long the TV is on, kept on this TV.")

        fun helper() = ModulePlaceholderSection(HELPER, "Helper", R.drawable.ic_set_helper, HELPER_PROBE,
            "The optional companion for remote shortcuts such as double-Back to Home.")

        fun browser() = ModulePlaceholderSection(BROWSER, "Browser", R.drawable.ic_set_browser, BROWSER_PROBE,
            "A simple web browser built for the remote.")

        fun devices() = ModulePlaceholderSection(DEVICES, "Devices", R.drawable.ic_set_devices, DEVICES_PROBE,
            "Remotes, speakers and other devices connected to this TV.")
    }
}
