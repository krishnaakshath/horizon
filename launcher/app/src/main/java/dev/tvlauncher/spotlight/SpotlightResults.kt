package dev.tvlauncher.spotlight

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.ui.SpotlightSearch

/** A Settings page Spotlight can open (a sidebar section of the launcher's own Settings). */
data class SettingsHit(val id: String, val title: String, val iconRes: Int)

/** One tile in the Spotlight results row. */
sealed class SpotlightResult {
    data class App(val app: AppEntry) : SpotlightResult()
    data class Setting(val hit: SettingsHit) : SpotlightResult()
}

/**
 * What the results row shows for a query under a [SearchScope]:
 * - APPS: ranked apps ([SpotlightSearch]); nothing for an empty query.
 * - ALL: with a query, ranked apps then up to [MAX_SETTINGS] matching Settings pages (within
 *   [SpotlightSearch.LIMIT] tiles in all); for an empty query, the recently opened apps.
 */
object SpotlightResults {
    const val MAX_SETTINGS = 3
    const val MAX_RECENT = 8

    data class Shown(val results: List<SpotlightResult>, val visible: Boolean, val recent: Boolean)

    fun compute(
        query: String,
        scope: SearchScope,
        apps: List<AppEntry>,
        settings: List<SettingsHit>,
        recentPackages: List<String>
    ): Shown {
        val q = SpotlightSearch.normalize(query)
        if (scope == SearchScope.APPS) {
            return Shown(SpotlightSearch.rank(query, apps).map { SpotlightResult.App(it) }, q.isNotEmpty(), recent = false)
        }
        if (q.isEmpty()) {
            val byPkg = apps.associateBy { it.packageName }
            val recent = recentPackages.mapNotNull { byPkg[it] }.take(MAX_RECENT).map { SpotlightResult.App(it) }
            return Shown(recent, recent.isNotEmpty(), recent = true)
        }
        val hits = rankSettings(q, settings).take(MAX_SETTINGS)
        val appHits = SpotlightSearch.rank(query, apps).take(SpotlightSearch.LIMIT - hits.size)
        return Shown(appHits.map { SpotlightResult.App(it) } + hits.map { SpotlightResult.Setting(it) }, true, recent = false)
    }

    /** Title prefix first, then a later word's prefix, then substring; input order within a tier. */
    fun rankSettings(normalizedQuery: String, settings: List<SettingsHit>): List<SettingsHit> =
        settings.mapNotNull { s ->
            val t = SpotlightSearch.normalize(s.title)
            val tier = when {
                t.startsWith(normalizedQuery) -> 0
                t.split(' ').drop(1).any { it.startsWith(normalizedQuery) } -> 1
                t.contains(normalizedQuery) -> 2
                else -> null
            }
            tier?.let { it to s }
        }.sortedBy { it.first }.map { it.second }

    /** Most recent first, no duplicates, at most [MAX_RECENT] (kept a little longer for hidden apps). */
    fun record(recent: List<String>, pkg: String, keep: Int = MAX_RECENT * 2): List<String> =
        (listOf(pkg) + recent.filter { it != pkg }).take(keep)
}
