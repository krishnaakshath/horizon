package dev.tvlauncher.appsettings

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import dev.tvlauncher.R
import dev.tvlauncher.home.HomeSounds
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.sound.SoundPlayer
import dev.tvlauncher.sound.SoundPrefs
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.SoundSettingsPanel
import dev.tvlauncher.ui.SystemActions
import java.util.concurrent.atomic.AtomicReference

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
        else "Not granted" to "If this TV has no usage access page, run this once from a computer:\n${UsageAccess.ADB_LINE}"

    fun installedValue(installed: Boolean): String = if (installed) "Installed" else "Not installed"
}

/**
 * Hosts [SoundSettingsPanel] in a bounded-height container. Previews and edits go through the
 * home screen's player when it is alive ([HomeSounds.active]); otherwise the page owns a player
 * that it releases when it is detached.
 */
object SoundsSection : SettingsSection {
    override val id = ModulePlaceholderSection.SOUNDS
    override val title = "Sounds"
    override val iconRes = R.drawable.ic_set_sounds

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val prefs = SoundPrefs(context)
        val home = HomeSounds.active
        val latest = AtomicReference(prefs.load())
        val own = if (home == null) SoundPlayer(context.applicationContext, settings = { latest.get() }) else null
        val player = home?.player ?: own
        val panel = SoundSettingsPanel(
            context, prefs, player,
            player?.customDir()?.path ?: "Android/data/${context.packageName}/files/sounds",
            onChanged = { s ->
                // The panel has saved [s] already; make the next sounds use it.
                if (home != null) home.applied(s) else { latest.set(s); own?.prewarm() }
            },
            embedded = true
        )
        own?.prewarm()
        return object : FrameLayout(context) {
            override fun onDetachedFromWindow() {
                own?.release()
                super.onDetachedFromWindow()
            }
        }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
    }
}

object InsightsSection : SettingsSection {
    override val id = ModulePlaceholderSection.INSIGHTS
    override val title = "Insights"
    override val iconRes = R.drawable.ic_set_insights

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.lead("What you watch most and how long the TV is on. Everything stays on this TV.")
        page.row("insights.open", "Open Insights", RowKind.Action {
            if (!SystemActions.insights(context)) nav.notify("Insights could not be opened")
        }, subtitle = "Today, this week and your top apps", iconRes = R.drawable.ic_set_insights)
        val granted = UsageAccess.hasAccess(context)
        val (value, note) = ModuleSections.usageAccessStatus(granted)
        page.row("insights.access", "Usage access", RowKind.Info(value),
            subtitle = "Lets the launcher read which app is on screen", iconRes = R.drawable.ic_set_eye)
        if (!granted) {
            page.row("insights.grant", "Open usage access settings", RowKind.Action {
                if (!UsageAccess.openSettings(context)) nav.notify("This TV has no usage access page; use the adb line below")
            })
        }
        note?.let { page.note(it) }
        return page
    }
}

object BrowserSection : SettingsSection {
    override val id = ModulePlaceholderSection.BROWSER
    override val title = "Browser"
    override val iconRes = R.drawable.ic_set_browser

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.lead("A simple web browser built for the remote.")
        val installed = ModuleProbe.isAppInstalled(context, QuickActions.BROWSER_PACKAGE)
        if (installed) {
            page.row("browser.open", "Open Browser", RowKind.Action {
                if (!SystemActions.launchPackage(context, QuickActions.BROWSER_PACKAGE)) nav.notify("The browser could not be opened")
            }, subtitle = "Also in Spotlight's quick actions", iconRes = R.drawable.ic_set_browser)
        } else {
            page.row("browser.status", "Browser", RowKind.Info(ModuleSections.installedValue(false)),
                subtitle = "Install ${QuickActions.BROWSER_PACKAGE} to use it", iconRes = R.drawable.ic_set_browser)
        }
        return page
    }
}

/** Link to the separate Launcher Helper app (double-Back to Home, Spotlight shortcut). */
object HelperSection : SettingsSection {
    override val id = ModulePlaceholderSection.HELPER
    override val title = "Helper"
    override val iconRes = R.drawable.ic_set_helper

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.lead("The optional companion for remote shortcuts such as double-Back to Home.")
        if (ModuleProbe.isAppInstalled(context, ModuleSections.HELPER_PACKAGE)) {
            page.row("helper.open", "Launcher Helper", RowKind.Action {
                val i = Intent().setClassName(ModuleSections.HELPER_PACKAGE, ModuleSections.HELPER_ACTIVITY)
                if (!SystemActions.start(context, i)) nav.notify("Launcher Helper could not be opened")
            }, subtitle = "Opens the helper's own settings", iconRes = R.drawable.ic_set_helper)
            page.note("The helper works only after it is turned on under Settings > Accessibility on the TV.")
        } else {
            page.row("helper.status", "Launcher Helper", RowKind.Info(ModuleSections.installedValue(false)),
                subtitle = "Install the helper APK (${ModuleSections.HELPER_PACKAGE}) to use it", iconRes = R.drawable.ic_set_helper)
        }
        return page
    }
}
