package dev.tvlauncher.discover

/** One card on the Discover page. */
data class DiscoverItem(val app: PopularApp, val action: DiscoverActions.Action)

data class DiscoverSection(val key: String, val title: String, val items: List<DiscoverItem>)

/**
 * What the Discover page shows. Installed catalog apps are not offered again; they are listed in
 * [installed] so the page can say "you have everything" when nothing is left.
 */
data class DiscoverState(
    val region: String?,
    val sections: List<DiscoverSection>,
    val installed: List<PopularApp>
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

/** Pure Discover page builder. */
object DiscoverModel {
    const val RECOMMENDED_KEY = "recommended"
    const val RECOMMENDED_TITLE = "Recommended for you"
    const val RECOMMENDED_MAX = 8

    /** Regional favourites that lead "Recommended for you" in a region (in this order). */
    val REGION_PICKS: Map<String, List<String>> = mapOf(
        "IN" to listOf("in.startv.hotstar", "com.graymatrix.did", "com.sonyliv", "com.jio.media.jiobeats", "com.mxtech.videoplayer.ad"),
        "US" to listOf("com.hulu.livingroomplus", "com.peacocktv.peacockandroid"),
        "GB" to listOf("bbc.iplayer.android")
    )

    /** Popularity first, then label (case-insensitive), then id: total and stable. */
    val ORDER: Comparator<PopularApp> = compareByDescending<PopularApp> { it.popularity }
        .thenBy { it.label.lowercase() }
        .thenBy { it.id }

    fun build(
        installed: Set<String>,
        region: String?,
        hidden: Set<String>,
        getAppsInstalled: Boolean = false
    ): DiscoverState {
        val code = RegionGuess.normalize(region)
        val inRegion = PopularApps.forRegion(code).filter { it.id !in hidden }
        val (have, offer) = inRegion.partition { it.packageName in installed }
        val item = { a: PopularApp -> DiscoverItem(a, DiscoverActions.decide(a, installed, getAppsInstalled)) }

        val offerByPkg = offer.associateBy { it.packageName }
        val picks = REGION_PICKS[code].orEmpty().mapNotNull { offerByPkg[it] }
        val rest = offer.filter { it !in picks }.sortedWith(ORDER)
        val recommended = (picks + rest.filter { it.tvReady }).take(RECOMMENDED_MAX)

        val sections = ArrayList<DiscoverSection>()
        if (recommended.isNotEmpty()) sections += DiscoverSection(RECOMMENDED_KEY, RECOMMENDED_TITLE, recommended.map(item))
        val leftover = offer.filter { it !in recommended }
        for (cat in AppCategory.values()) {
            val apps = leftover.filter { it.category == cat }.sortedWith(ORDER)
            if (apps.isNotEmpty()) sections += DiscoverSection(cat.name, cat.title, apps.map(item))
        }
        return DiscoverState(code, sections, have.sortedWith(ORDER))
    }

    /** The one-line category text under a card. */
    fun subtitle(app: PopularApp): String =
        if (app.tvReady) app.category.title else "${app.category.title} · TV version unconfirmed"
}
