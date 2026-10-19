package dev.tvlauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import dev.tvlauncher.ambient.AmbientActivity
import dev.tvlauncher.appsettings.AppSettingsActivity

/** System shortcuts. Every launch is guarded so a missing handler never crashes. */
object SystemActions {
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun settings(context: Context) = start(context, Intent(Settings.ACTION_SETTINGS))

    /** A specific settings page, falling back to the main Settings screen. */
    fun settingsPage(context: Context, action: String) = start(context, Intent(action)) || settings(context)

    fun appInfo(context: Context, pkg: String) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

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
        QuickActions.Item.SETTINGS -> launcherSettings(context) || settings(context)
        QuickActions.Item.NETWORK -> settingsPage(context, Settings.ACTION_WIFI_SETTINGS)
        QuickActions.Item.BLUETOOTH -> settingsPage(context, Settings.ACTION_BLUETOOTH_SETTINGS)
        QuickActions.Item.SOUND -> settingsPage(context, Settings.ACTION_SOUND_SETTINGS)
        // Fallbacks only: the home screen opens its Devices panel / AmbientActivity itself.
        QuickActions.Item.DEVICES -> launcherSettings(context) || settings(context)
        QuickActions.Item.AMBIENT -> ambient(context)
        QuickActions.Item.INSIGHTS -> insights(context)
        QuickActions.Item.BROWSER -> launchPackage(context, QuickActions.BROWSER_PACKAGE)
    }

    /** The launcher's own settings screen ([AppSettingsActivity]); false if it does not resolve. */
    fun launcherSettings(context: Context): Boolean {
        val i = AppSettingsActivity.intent(context)
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
