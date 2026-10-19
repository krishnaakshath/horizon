package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import dev.tvlauncher.R

/**
 * Sidebar slot for a feature that ships in its own module. The module replaces this entry by
 * calling [SettingsRegistry.register] with a section of the same [id]. Until then the page says
 * the module is coming (when [probeClass] is absent) or that it is installed but not registered.
 */
class ModulePlaceholderSection(
    override val id: String,
    override val title: String,
    override val iconRes: Int,
    private val probeClass: String,
    private val blurb: String
) : SettingsSection {
    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.lead(blurb)
        if (!ModuleProbe.isPresent(probeClass)) {
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

        /** Class names probed (never instantiated) to tell "coming" from "installed". */
        const val SOUNDS_CLASS = "dev.tvlauncher.ui.SoundSettingsPanel"
        const val INSIGHTS_CLASS = "dev.tvlauncher.insights.InsightsSettingsSection"
        const val HELPER_CLASS = "dev.tvlauncher.helper.HelperSettingsSection"
        const val BROWSER_CLASS = "dev.tvlauncher.browser.BrowserSettingsSection"

        fun sounds() = ModulePlaceholderSection(SOUNDS, "Sounds", R.drawable.ic_set_sounds, SOUNDS_CLASS,
            "Navigation and launch sounds for the home screen.")

        fun insights() = ModulePlaceholderSection(INSIGHTS, "Insights", R.drawable.ic_set_insights, INSIGHTS_CLASS,
            "What you watch most and how long the TV is on, kept on this TV.")

        fun helper() = ModulePlaceholderSection(HELPER, "Helper", R.drawable.ic_set_helper, HELPER_CLASS,
            "The optional companion for remote shortcuts such as double-Back to Home.")

        fun browser() = ModulePlaceholderSection(BROWSER, "Browser", R.drawable.ic_set_browser, BROWSER_CLASS,
            "A simple web browser built for the remote.")
    }
}
