package dev.tvlauncher.apps

import dev.tvlauncher.settings.LauncherSettings

object AppOrdering {
    fun arrange(apps: List<AppEntry>, s: LauncherSettings): List<AppEntry> {
        val byPackage = LinkedHashMap<String, AppEntry>()
        for (a in apps) if (a.packageName !in s.hidden) byPackage.putIfAbsent(a.packageName, a)

        val result = ArrayList<AppEntry>(byPackage.size)
        val placed = HashSet<String>()
        for (pkg in s.favorites + s.order) {
            val app = byPackage[pkg] ?: continue
            if (placed.add(pkg)) result.add(app)
        }
        val rest = byPackage.values.filter { it.packageName !in placed }
            .sortedWith(
                compareBy<AppEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                    .thenBy { it.packageName }
            )
        result.addAll(rest)
        return result
    }
}
