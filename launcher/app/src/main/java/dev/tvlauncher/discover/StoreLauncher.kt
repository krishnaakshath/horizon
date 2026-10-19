package dev.tvlauncher.discover

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/**
 * Starts whatever a Discover card decided ([DiscoverActions]). Every start is guarded: a missing
 * handler or a SecurityException just moves on to the next candidate, and the caller gets a
 * short message to show instead of a crash.
 */
object StoreLauncher {
    const val GET_APPS_MISSING_TEXT = "Get Apps isn't installed"

    /** Performs [item]'s action. Returns null on success, else a message for a toast. */
    fun perform(context: Context, item: DiscoverItem): String? = when (item.action) {
        DiscoverActions.Action.OPEN -> if (openApp(context, item.app.packageName)) null else "Couldn't open ${item.app.label}"
        DiscoverActions.Action.OPEN_PLAY_STORE -> if (openPlayStore(context, item.app.packageName)) null else PlayStoreLinks.UNAVAILABLE_TEXT
        DiscoverActions.Action.OPEN_GET_APPS -> {
            val link = GetAppsLink.forSource(item.app.source)
            when {
                link != null && openGetApps(context, link) -> null
                item.app.onPlay && openPlayStore(context, item.app.packageName) -> null
                else -> GET_APPS_MISSING_TEXT
            }
        }
        DiscoverActions.Action.NONE -> "${item.app.label} isn't available here"
    }

    /** Like [perform] and shows the toast itself. */
    fun performWithToast(context: Context, item: DiscoverItem) {
        perform(context, item)?.let { msg ->
            try { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } catch (e: Exception) { }
        }
    }

    /** Tries [PlayStoreLinks.detailsIntents] in order; false when none resolves. */
    fun openPlayStore(context: Context, pkg: String): Boolean {
        for (c in PlayStoreLinks.detailsIntents(pkg)) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(c.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.targetPackage?.let { i.setPackage(it) }
            if (tryStart(context, i)) return true
        }
        return false
    }

    fun openApp(context: Context, pkg: String): Boolean {
        val i = launchIntent(context, pkg) ?: return false
        return tryStart(context, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Starts Get Apps with the [GetAppsLink] extras as a hint (see its contract). */
    fun openGetApps(context: Context, link: GetAppsLink): Boolean {
        val i = launchIntent(context, GetAppsLink.PACKAGE) ?: return false
        i.putExtra(GetAppsLink.EXTRA_SOURCE, link.source).putExtra(GetAppsLink.EXTRA_VALUE, link.value)
        return tryStart(context, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isGetAppsInstalled(context: Context): Boolean = launchIntent(context, GetAppsLink.PACKAGE) != null

    /**
     * Catalog packages that are installed and launchable. Visible at targetSdk 30 through the
     * MAIN/LAUNCHER and MAIN/LEANBACK_LAUNCHER `<queries>`; the controller may pass its own
     * installed set (AppRepository) instead.
     */
    fun installedCatalogPackages(context: Context): Set<String> =
        PopularApps.all.map { it.packageName }.filterTo(HashSet()) { launchIntent(context, it) != null }

    private fun launchIntent(context: Context, pkg: String): Intent? = try {
        val pm = context.packageManager
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }

    private fun tryStart(context: Context, i: Intent): Boolean = try {
        context.startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        false
    }
}
