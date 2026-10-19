package dev.tvlauncher.safemode

import dev.tvlauncher.apps.AppEntry

/** Pure model of the safe-mode list: fixed rows first, then apps (leanback first). */
object SafeModeList {
    const val HEADER = "Safe mode — the main launcher crashed repeatedly"
    const val STOCK_LAUNCHER = "com.google.android.tvlauncher"
    const val APPS_FAILED = "Could not list apps"
    const val ENABLE_STOCK_HINT = "To get the stock launcher back: Settings → Apps → TV launcher → Enable"

    sealed class Row {
        object TryNormal : Row()
        object SystemSettings : Row()
        object StockLauncher : Row()
        data class App(val app: AppEntry) : Row()
        /** Information only; OK does nothing. */
        data class Notice(val text: String) : Row()
    }

    fun build(
        leanback: List<AppEntry>,
        launcher: List<AppEntry>,
        ownPackage: String,
        stockLauncherAvailable: Boolean,
        appsFailed: Boolean = false
    ): List<Row> {
        val rows = ArrayList<Row>()
        rows += Row.TryNormal
        rows += Row.SystemSettings
        if (stockLauncherAvailable) rows += Row.StockLauncher
        val seen = HashSet<String>().apply { add(ownPackage); add(STOCK_LAUNCHER) }
        for (group in listOf(leanback, launcher)) {
            group.filter { seen.add(it.packageName) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .mapTo(rows) { Row.App(it) }
        }
        if (appsFailed) rows += Row.Notice(APPS_FAILED)
        return rows
    }

    /** The minimal screen (safe mode crashed once): no app list, system Settings first. */
    fun minimal(stockLauncherAvailable: Boolean): List<Row> = buildList {
        add(Row.SystemSettings)
        if (stockLauncherAvailable) add(Row.StockLauncher) else add(Row.Notice(ENABLE_STOCK_HINT))
        add(Row.TryNormal)
    }

    fun label(row: Row): String = when (row) {
        Row.TryNormal -> "Try normal launcher again"
        Row.SystemSettings -> "Open system settings"
        Row.StockLauncher -> "Open stock launcher"
        is Row.App -> row.app.label
        is Row.Notice -> row.text
    }

    /** The stock launcher is offered only when it is installed, enabled and still a HOME activity. */
    fun stockAvailable(installed: Boolean, enabled: Boolean, hasHome: Boolean): Boolean = installed && enabled && hasHome
}
