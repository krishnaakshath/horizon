package dev.tvlauncher.search

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.tvlauncher.discover.GetAppsLink
import dev.tvlauncher.ui.QuickActions

/**
 * Hands a search to another app. The launcher itself never goes online: the Browser companion
 * gets the text in [EXTRA_QUERY] (it turns words into a search and an address into a page), the
 * YouTube app gets a search intent. Every start is guarded and reports whether it worked.
 */
object WebLaunch {
    /** Same string as the Browser companion's ExternalQuery.EXTRA_QUERY. */
    const val EXTRA_QUERY = "dev.tvlauncher.browser.extra.QUERY"

    fun browser(context: Context, text: String): Boolean {
        val i = launchIntent(context, QuickActions.BROWSER_PACKAGE) ?: return false
        return start(context, i.putExtra(EXTRA_QUERY, text.trim()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The YouTube app's own search, then a YouTube link in that app, then the site in the Browser. */
    fun youtube(context: Context, query: String): Boolean {
        val q = query.trim()
        val pkg = SearchIndex.YOUTUBE_PACKAGE
        if (installed(context, pkg)) {
            val search = Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, q).putExtra("query", q)
            if (start(context, search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(SearchIndex.youtubeUrl(q))).setPackage(pkg)
            if (start(context, view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
        }
        return browser(context, SearchIndex.youtubeUrl(q))
    }

    /** The store companion, to get the Browser. */
    fun store(context: Context): Boolean {
        val i = launchIntent(context, GetAppsLink.PACKAGE) ?: return false
        return start(context, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    private fun launchIntent(context: Context, pkg: String): Intent? = try {
        val pm = context.packageManager
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    } catch (e: RuntimeException) {
        null
    }

    private fun start(context: Context, i: Intent): Boolean = try {
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
