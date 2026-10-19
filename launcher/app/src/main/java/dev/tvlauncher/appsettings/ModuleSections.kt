package dev.tvlauncher.appsettings

import android.content.Context
import android.content.Intent
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.ui.SystemActions

/**
 * The real Settings pages of the wave-1 modules. [registerAll] runs once from
 * LauncherApp.onCreate, so the first Settings screen already shows them in their placeholders' slots.
 */
object ModuleSections {
    val IDS = listOf(
        ModulePlaceholderSection.SOUNDS, ModulePlaceholderSection.INSIGHTS,
        ModulePlaceholderSection.HELPER, ModulePlaceholderSection.BROWSER
    )
    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
    const val HELPER_ACTIVITY = "dev.tvlauncher.helper.HelperSettingsActivity"

    fun registerAll(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(SoundsSection)
        registry.register(InsightsSection)
        registry.register(HelperSection)
        registry.register(BrowserSection)
    }

    /** Value and an optional note for the "Usage access" row. */
    fun usageAccessStatus(granted: Boolean): Pair<String, String?> =
        if (granted) "Granted" to null
        else "Not granted" to dev.tvlauncher.setup.SetupCopy.HELPER_LINE

    fun installedValue(installed: Boolean): String = if (installed) "Installed" else "Not installed"
}

/** Hosts [SoundsPage]: SettingsRow-based, with choosers and an Advanced block. */
object SoundsSection : SettingsSection, SpecifiedPage {
    override val id = ModulePlaceholderSection.SOUNDS
    override val title = "Sounds"
    override val iconRes = R.drawable.ic_set_sounds
    override val group = SettingsGroup.SOUND
    override val description = "Navigation and launch sounds on the home screen."
    override val spec = SoundsPage.SPEC

    override fun buildPage(context: Context, nav: SettingsNav): View = SoundsPage.build(context, nav)
}

object InsightsSection : SettingsSection, SpecifiedPage {
    override val id = ModulePlaceholderSection.INSIGHTS
    override val title = "Insights"
    override val iconRes = R.drawable.ic_set_insights
    override val group = SettingsGroup.APPS
    override val description = "What you watch most. Everything stays on this TV."
    override val spec = PageSpec(id, listOf("insights.open", "insights.access"), listOf("insights.setup"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        page.row("insights.open", "Open Insights", RowKind.Action {
            if (!SystemActions.insights(context)) nav.notify("Insights could not be opened")
        }, subtitle = "Today, this week and your top apps", iconRes = R.drawable.ic_set_insights)
        val granted = UsageAccess.hasAccess(context)
        val (value, _) = ModuleSections.usageAccessStatus(granted)
        page.row("insights.access", "Usage access", if (granted) RowKind.Info(value) else RowKind.Action(value) {
            if (!UsageAccess.openSettings(context)) nav.notify("This TV has no usage access page; see Advanced")
        }, subtitle = "Lets the launcher see which app is on screen", iconRes = R.drawable.ic_set_eye)
        page.advanced(nav, "insights.advanced", "No usage access page on this TV?") { adv ->
            adv.row("insights.setup", "Open setup", RowKind.Action {
                if (!dev.tvlauncher.setup.SetupStatus.open(context)) nav.notify("Setup could not be opened")
            }, subtitle = "The setup checklist shows what this TV needs", iconRes = R.drawable.ic_sc_settings)
        }
        return page
    }
}

/** How an optional companion app is shown and opened. */
internal class ModuleUi(val addOn: AddOn, val iconRes: Int, val openNote: String?, val open: (Context) -> Boolean)

internal object ModuleUis {
    val all: List<ModuleUi> = AddOns.all.map { a ->
        when (a.id) {
            "browser" -> ModuleUi(a, R.drawable.ic_set_browser, "Also in Spotlight's quick actions") { c ->
                SystemActions.launchPackage(c, a.packageName)
            }
            "getapps" -> ModuleUi(a, R.drawable.ic_set_store, null) { c -> SystemActions.launchPackage(c, a.packageName) }
            "weather" -> ModuleUi(a, R.drawable.ic_set_weather, "Location and units") { c ->
                WeatherCompanion.settingsIntent(c)?.let { SystemActions.start(c, it) } ?: SystemActions.launchPackage(c, a.packageName)
            }
            else -> ModuleUi(a, R.drawable.ic_set_helper, "Double-Back and a shortcut key in every app") { c ->
                SystemActions.start(c, Intent().setClassName(ModuleSections.HELPER_PACKAGE, ModuleSections.HELPER_ACTIVITY))
            }
        }
    }

    fun byId(id: String): ModuleUi = all.first { it.addOn.id == id }
}

/** A companion app's page: its status and ONE action, "Open" or "How to install". */
internal object ModulePage {
    fun spec(sectionId: String, prefix: String) = PageSpec(sectionId, listOf("$prefix.status", "$prefix.action"))

    fun build(context: Context, nav: SettingsNav, m: ModuleUi, prefix: String = m.addOn.id): SettingsPage {
        val page = SettingsPage(context, nav.style, spec(m.addOn.sectionId ?: m.addOn.id, prefix))
        val a = m.addOn
        val installed = ModuleProbe.isAppInstalled(context, a.packageName)
        val act = {
            if (installed) {
                if (!m.open(context)) nav.notify("${a.title} could not be opened")
            } else {
                nav.push("How to install") { c, n -> TextPage(c, n.style).apply { setText(AddOns.howToInstall(a)) } }
            }
        }
        page.row("$prefix.status", a.title,
            RowKind.Action(AddOns.chip(installed), chevron = false, chip = if (installed) Chip.POSITIVE else Chip.NEUTRAL) { act() },
            subtitle = a.adds, iconRes = m.iconRes)
        page.row("$prefix.action", AddOns.primaryAction(installed), RowKind.Action { act() },
            subtitle = if (installed) m.openNote else a.packageName,
            iconRes = if (installed) R.drawable.ic_set_chevron else R.drawable.ic_set_import)
        return page
    }
}

/** Base for the sidebar pages of companion apps: listed only while the app is installed. */
abstract class CompanionSection(private val addOnId: String) : SettingsSection, SpecifiedPage {
    private val ui get() = ModuleUis.byId(addOnId)
    override val title: String get() = ui.addOn.title
    override val iconRes: Int get() = ui.iconRes
    override val group get() = SettingsGroup.APPS
    override val availability: SectionAvailability get() = SectionAvailability.AppInstalled(ui.addOn.packageName)
    override val description: String get() = ui.addOn.adds + "."
    override val spec: PageSpec get() = ModulePage.spec(id, addOnId)

    override fun buildPage(context: Context, nav: SettingsNav): View = ModulePage.build(context, nav, ui)
}

object BrowserSection : CompanionSection("browser") {
    override val id = ModulePlaceholderSection.BROWSER
}

/** Link to the separate Launcher Helper app (double-Back to Home, Spotlight shortcut). */
object HelperSection : CompanionSection("helper") {
    override val id = ModulePlaceholderSection.HELPER
}

object GetAppsSection : CompanionSection("getapps") {
    const val ID = "getapps"
    override val id = ID
}

/** Every optional companion app with its status; the only place uninstalled ones appear. */
object AddOnsSection : SettingsSection, SpecifiedPage {
    const val ID = "addons"
    override val id = ID
    override val title = "Add-ons"
    override val iconRes = R.drawable.ic_set_addons
    override val group = SettingsGroup.APPS
    override val description = "Optional companion apps and what each one adds."
    override val spec get() = PageSpec(ID, AddOns.all.map { "addons.${it.id}" })

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        for (m in ModuleUis.all) {
            val a = m.addOn
            val installed = ModuleProbe.isAppInstalled(context, a.packageName)
            page.row("addons.${a.id}", a.title,
                RowKind.Action(AddOns.chip(installed), chevron = true, chip = if (installed) Chip.POSITIVE else Chip.NEUTRAL) {
                    nav.push(a.title) { c, n -> ModulePage.build(c, n, m, prefix = "addons.${a.id}.page") }
                }, subtitle = a.adds, iconRes = m.iconRes)
        }
        return page
    }
}
