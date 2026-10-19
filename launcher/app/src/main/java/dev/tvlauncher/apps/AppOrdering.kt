package dev.tvlauncher.apps

import dev.tvlauncher.settings.LauncherSettings

object AppOrdering {
    /**
     * Favorites, then the saved order, then everything else. Apps not placed by the user are
     * sorted by their index in [preferred] (if listed) and then alphabetically.
     */
    fun arrange(
        apps: List<AppEntry>,
        s: LauncherSettings,
        preferred: List<String> = emptyList()
    ): List<AppEntry> {
        val byPackage = LinkedHashMap<String, AppEntry>()
        for (a in apps) if (a.packageName !in s.hidden) byPackage.putIfAbsent(a.packageName, a)

        val result = ArrayList<AppEntry>(byPackage.size)
        val placed = HashSet<String>()
        for (pkg in s.favorites + s.order) {
            val app = byPackage[pkg] ?: continue
            if (placed.add(pkg)) result.add(app)
        }
        val rank = HashMap<String, Int>()
        preferred.forEachIndexed { i, p -> rank.putIfAbsent(p, i) }
        val rest = byPackage.values.filter { it.packageName !in placed }
            .sortedWith(
                compareBy<AppEntry> { rank[it.packageName] ?: Int.MAX_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenBy { it.packageName }
            )
        result.addAll(rest)
        return result
    }
}
