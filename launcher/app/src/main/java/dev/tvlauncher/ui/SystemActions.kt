package dev.tvlauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import dev.tvlauncher.ambient.AmbientActivity
import dev.tvlauncher.appsettings.AppSettingsActivity
import dev.tvlauncher.system.SystemLinks

/**
 * Launches from the home screen. Every launch is guarded so a missing handler never crashes.
 * Nothing here opens the TV's settings app: settings go to the launcher's own pages
 * ([launcherSettings]); the few TV-only screens live behind dev.tvlauncher.system.TvScreens.
 */
object SystemActions {
    /**
     * A system Settings screen (an implicit android.settings.* intent) is started only when this
     * TV resolves it, so a missing screen is a quiet false, never a system error.
     */
    fun canStart(context: Context, intent: Intent): Boolean {
        val implicitSystemScreen = intent.component == null && intent.`package` == null &&
            intent.action?.startsWith("android.settings.") == true
        if (!implicitSystemScreen) return true
        return try { context.packageManager.resolveActivity(intent, 0) != null } catch (e: Exception) { false }
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        if (!canStart(context, intent)) false
        else { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** App details in the launcher's own Settings > Apps page (open, version, size, stop, uninstall). */
    fun appInfo(context: Context, pkg: String) =
        start(context, AppSettingsActivity.intent(context, SystemLinks.APPS).putExtra(SystemLinks.EXTRA_SUBPAGE, SystemLinks.appPage(pkg)))

    /** Starts an app by package (leanback entry first); false when it is not installed. */
    fun launchPackage(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return start(context, i)
    }

    /** Insights screen (added by a separate task); a missing class is a guarded no-op. */
    fun insights(context: Context) = start(context, Intent().setClassName(context.packageName, INSIGHTS_CLASS))

    fun quickAction(context: Context, item: QuickActions.Item): Boolean = when (item) {
        QuickActions.Item.INPUTS -> inputs(context)
        QuickActions.Item.AMBIENT -> ambient(context)
        QuickActions.Item.ROOM -> false // handled on the home screen (HomeModules)
        QuickActions.Item.INSIGHTS -> insights(context)
        QuickActions.Item.BROWSER -> launchPackage(context, QuickActions.BROWSER_PACKAGE)
        QuickActions.Item.GET_APPS -> launchPackage(context, QuickActions.GET_APPS_PACKAGE)
        // Handled on the home screen (HomeModules); nothing to fall back to here.
        QuickActions.Item.RESTART, QuickActions.Item.DISCOVER, QuickActions.Item.BEDTIME -> false
        // The launcher's own sleep timer lives on TV & system > Power & sleep.
        QuickActions.Item.POWER, QuickActions.Item.SLEEP_TIMER ->
            launcherSettings(context, QuickActions.launcherSection(item), SystemLinks.POWER)
        // Settings, Wi-Fi, Bluetooth, Devices, Accessibility and Sound (Settings > Sound > Volume &
        // output, never the TV's sound menu): the launcher's own pages.
        else -> launcherSettings(context, QuickActions.launcherSection(item))
    }

    /** The launcher's own settings screen ([AppSettingsActivity]), optionally on a section and sub-page. */
    fun launcherSettings(context: Context, section: String? = null, subpage: String? = null): Boolean {
        val i = AppSettingsActivity.intent(context, section)
        subpage?.let { i.putExtra(SystemLinks.EXTRA_SUBPAGE, it) }
        if (context.packageManager.resolveActivity(i, 0) == null) return false
        return start(context, i)
    }

    /** Our ambient screen (explicit component, so nothing else can answer the action). */
    fun ambient(context: Context) = start(context, AmbientActivity.intent(context).setPackage(context.packageName))

    const val LAUNCHER_SETTINGS_ACTION = "dev.tvlauncher.action.SETTINGS"
    const val INSIGHTS_CLASS = "dev.tvlauncher.insights.InsightsActivity"

    fun inputs(context: Context): Boolean {
        val id = try {
            val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            val list = tim?.tvInputList.orEmpty().filter { it.isPassthroughInput }
            (list.firstOrNull { it.type == TvInputInfo.TYPE_HDMI } ?: list.firstOrNull())?.id
        } catch (e: Exception) {
            null
        } ?: return false
        return start(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))
    }
}
