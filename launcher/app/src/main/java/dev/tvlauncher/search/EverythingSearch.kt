package dev.tvlauncher.search

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import dev.tvlauncher.R
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.appsettings.AppSettingsActivity
import dev.tvlauncher.appsettings.SectionAvailability
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.discover.DiscoverActions
import dev.tvlauncher.discover.DiscoverItem
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.discover.GetAppsLink
import dev.tvlauncher.discover.PopularApp
import dev.tvlauncher.discover.PopularApps
import dev.tvlauncher.discover.RegionGuess
import dev.tvlauncher.discover.StoreLauncher
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.watch.WatchLauncher
import dev.tvlauncher.watch.WatchProgram
import java.util.Locale

/**
 * Spotlight's "Everything" scope: apps, Continue watching titles, Settings pages, quick actions,
 * popular apps to install, a calculator answer and web rows, ranked by [SearchRanker] and shown
 * grouped in [EverythingResults]. The panel owns the search field and calls [prepare] on open
 * and [update] for each query.
 */
class EverythingSearch(
    private val context: Context,
    val view: EverythingResults,
    private val host: Host
) {
    interface Host {
        fun launchApp(app: AppEntry, from: View)
        fun runAction(item: QuickActions.Item)
        fun closePanel()
        fun setQuery(text: String)
        fun bindAppIcon(app: AppEntry, view: ImageView)
    }

    private var items: List<SearchItem> = emptyList()
    private var installed: Set<String> = emptySet()
    private var browser = false
    private var youtube = false
    private var store = false
    var last: SearchResults = SearchResults(emptyList())
        private set

    init {
        view.binder = object : EverythingResults.Binder {
            override fun bindIcon(item: SearchItem, view: ImageView) = icon(item, view)
            override fun actionLabel(item: SearchItem): String? = label(item)
            override fun onPick(item: SearchItem, row: View) = pick(item, row)
        }
    }

    /** On open (and when the app list changes): everything searchable now. Main thread; cheap. */
    fun prepare(apps: List<AppEntry>, installedPackages: Set<String>, recent: List<String>, programs: List<WatchProgram>) {
        installed = installedPackages
        browser = QuickActions.BROWSER_PACKAGE in installedPackages
        store = GetAppsLink.PACKAGE in installedPackages
        youtube = SearchIndex.YOUTUBE_PACKAGE in installedPackages
        val availability = QuickActions.Availability(installedPackages)
        val actions = QuickActions.Item.values().filter { QuickActions.unavailableReason(it, availability) == null }
        items = try {
            SearchIndex.build(apps, recent, settingsEntries(), actions, programs, catalog(), installedPackages)
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    fun update(query: String): SearchResults {
        val web = SearchIndex.webRows(query, browser, youtube, store).map { SearchIndex.webItem(it, query) }
        last = SearchRanker.search(query, items, SearchIndex.answerItem(query), web)
        view.show(last)
        return last
    }

    private fun settingsEntries(): List<SettingsEntry> = try {
        val probe = object : SectionAvailability.Probe {
            override fun appInstalled(pkg: String) = pkg in installed
            override fun classPresent(name: String) = try { Class.forName(name); true } catch (e: Throwable) { false }
        }
        SettingsRegistry.shared.sections().filter { it.availability.resolve(probe) }
            .map { SettingsEntry(it.id, it.title, it.description, it.iconRes) }
    } catch (e: RuntimeException) {
        emptyList()
    }

    private fun catalog(): List<PopularApp> {
        val override = try { DiscoverStore(context).load().region } catch (e: RuntimeException) { null }
        return PopularApps.forRegion(RegionGuess.fromLocale(Locale.getDefault(), null, override))
    }

    private fun icon(item: SearchItem, v: ImageView) {
        when (val ref = item.ref) {
            is AppEntry -> host.bindAppIcon(ref, v)
            is WatchProgram -> appIcon(ref.packageName, v)
            is SettingsEntry -> view.tintIcon(v, ref.iconRes)
            is QuickActions.Item -> view.tintIcon(v, QuickActions.iconRes(ref))
            is PopularApp -> view.tintIcon(v, R.drawable.ic_disc_bag)
            is WebRow -> view.tintIcon(v, if (ref.kind == WebKind.GET_BROWSER) R.drawable.ic_set_store else R.drawable.ic_browser)
            else -> view.tintIcon(v, R.drawable.ic_info)
        }
    }

    /** A Continue watching row shows its app's icon (the program has no activity name). */
    private fun appIcon(pkg: String, v: ImageView) {
        try { v.setImageDrawable(context.packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { view.tintIcon(v, R.drawable.ic_np_play) }
    }

    private fun label(item: SearchItem): String? = when (val ref = item.ref) {
        is WatchProgram -> "Resume"
        is PopularApp -> DiscoverActions.label(DiscoverActions.decide(ref, installed, store))
        is SettingsEntry -> "Open"
        else -> null
    }

    private fun pick(item: SearchItem, row: View) {
        when (val ref = item.ref) {
            is AppEntry -> host.launchApp(ref, row)
            is WatchProgram -> {
                host.closePanel()
                if (!WatchLauncher.launch(context, ref)) toast("Couldn't open it")
            }
            is SettingsEntry -> {
                host.closePanel()
                SystemActions.start(context, AppSettingsActivity.intent(context, ref.id))
            }
            is QuickActions.Item -> host.runAction(ref)
            is PopularApp -> StoreLauncher.performWithToast(context, DiscoverItem(ref, DiscoverActions.decide(ref, installed, store)))
            is QuickAnswer.Answer -> host.setQuery(ref.value)
            is WebRow -> {
                val q = ref.query
                val ok = when (ref.kind) {
                    WebKind.WEB -> WebLaunch.browser(context, q)
                    WebKind.YOUTUBE -> WebLaunch.youtube(context, q)
                    WebKind.GET_BROWSER -> WebLaunch.store(context)
                }
                if (ok) host.closePanel() else toast("Couldn't open it")
            }
        }
    }

    private fun toast(msg: String) = try { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } catch (e: RuntimeException) { }
}
