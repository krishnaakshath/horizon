package dev.tvlauncher.search

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.discover.PopularApp
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.watch.WatchModel
import dev.tvlauncher.watch.WatchProgram

/** A launcher Settings page as the search sees it: sidebar title and the line under it. */
data class SettingsEntry(val id: String, val title: String, val description: String, val iconRes: Int)

/** What a web row does. */
enum class WebKind { WEB, YOUTUBE, GET_BROWSER }

/** A web row's [kind] and the [query] it was made for. */
data class WebRow(val kind: WebKind, val query: String)

/**
 * Builds the Everything search's items from what the home screen already knows. Pure: the
 * Spotlight panel passes plain lists in and gets [SearchItem]s whose [SearchItem.ref] is the
 * original object (an [AppEntry], a [SettingsEntry], a [QuickActions.Item], a [WatchProgram],
 * a [PopularApp], a [WebRow] or a [QuickAnswer.Answer]).
 */
object SearchIndex {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"

    fun build(
        apps: List<AppEntry>,
        recentPackages: List<String>,
        settings: List<SettingsEntry>,
        actions: List<QuickActions.Item>,
        programs: List<WatchProgram>,
        catalog: List<PopularApp>,
        installedPackages: Set<String>
    ): List<SearchItem> {
        val out = ArrayList<SearchItem>()
        val recency = recentPackages.withIndex().associate { it.value to it.index }
        val seen = HashSet<String>()
        for (a in apps) {
            if (!seen.add(a.packageName)) continue
            out += SearchItem(SearchSection.APPS, "app:${a.packageName}", a.label, recency = recency[a.packageName] ?: -1, ref = a)
        }
        programs.forEachIndexed { i, p ->
            val app = apps.firstOrNull { it.packageName == p.packageName }?.label
            val sub = listOfNotNull(WatchModel.episodeLine(p), app).joinToString(" · ").ifEmpty { null }
            out += SearchItem(SearchSection.CONTINUE, "watch:${p.key}", p.title, subtitle = sub,
                keywords = listOfNotNull(p.episodeTitle), recency = i, ref = p)
        }
        for (s in settings) {
            out += SearchItem(SearchSection.SETTINGS, "settings:${s.id}", s.title, subtitle = s.description.ifEmpty { null }, ref = s)
        }
        for (item in actions) {
            out += SearchItem(SearchSection.ACTIONS, "action:${item.name}", item.title, keywords = actionKeywords(item), ref = item)
        }
        for (p in catalog) {
            if (p.packageName in installedPackages || !seen.add(p.packageName)) continue
            out += SearchItem(SearchSection.DISCOVER, "discover:${p.id}", p.label, subtitle = p.category.title, ref = p)
        }
        return out
    }

    /** Other words people use for a quick action. */
    fun actionKeywords(item: QuickActions.Item): List<String> = when (item) {
        QuickActions.Item.INPUTS -> listOf("HDMI", "source")
        QuickActions.Item.NETWORK -> listOf("Wi-Fi", "wifi", "internet")
        QuickActions.Item.SOUND -> listOf("volume", "audio")
        QuickActions.Item.POWER -> listOf("off", "standby", "restart TV")
        QuickActions.Item.SLEEP_TIMER -> listOf("timer", "sleep")
        QuickActions.Item.AMBIENT -> listOf("screensaver", "photos")
        QuickActions.Item.ROOM -> listOf("brightness", "picture", "lighting")
        QuickActions.Item.DISCOVER -> listOf("install", "popular apps")
        QuickActions.Item.GET_APPS -> listOf("install", "store")
        else -> emptyList()
    }

    /**
     * Web rows for a non-empty query: the Browser companion searches the web (and YouTube in the
     * YouTube app, else on its site in the Browser); without the Browser, one row offers it in
     * the store companion when that is installed. Nothing otherwise.
     */
    fun webRows(query: String, browser: Boolean, youtube: Boolean, store: Boolean): List<WebKind> {
        if (query.isBlank()) return emptyList()
        val out = ArrayList<WebKind>()
        if (browser) out += WebKind.WEB
        if (youtube || browser) out += WebKind.YOUTUBE
        if (!browser && store) out += WebKind.GET_BROWSER
        return out
    }

    fun webItem(kind: WebKind, query: String): SearchItem {
        val q = query.trim()
        return when (kind) {
            WebKind.WEB -> SearchItem(SearchSection.WEB, "web:web", "Search the web for “$q”", subtitle = "Opens ${Brand.BROWSER}", ref = WebRow(kind, q))
            WebKind.YOUTUBE -> SearchItem(SearchSection.WEB, "web:youtube", "Search YouTube for “$q”", ref = WebRow(kind, q))
            WebKind.GET_BROWSER -> SearchItem(SearchSection.WEB, "web:get", "Search the web with ${Brand.BROWSER}",
                subtitle = "Get it in ${Brand.STORE}", ref = WebRow(kind, q))
        }
    }

    /** The calculator row, or null when [query] is not arithmetic. */
    fun answerItem(query: String): SearchItem? = QuickAnswer.answer(query)?.let {
        SearchItem(SearchSection.ANSWER, "answer", "= ${it.value}", subtitle = it.expression, ref = it)
    }

    /** The YouTube search page on its site (for the Browser when the app is missing). */
    fun youtubeUrl(query: String): String = "https://www.youtube.com/results?search_query=" + encode(query.trim())

    /** Percent-encoding for a query parameter (UTF-8; space as +). */
    fun encode(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            when {
                c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code -> sb.append(c.toChar())
                c == ' '.code -> sb.append('+')
                else -> sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
        }
        return sb.toString()
    }
}
