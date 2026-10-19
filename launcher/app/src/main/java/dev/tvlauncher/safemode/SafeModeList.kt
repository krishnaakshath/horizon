package dev.tvlauncher.safemode

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.brand.Brand

/** Pure model of the safe-mode list: fixed rows first, then apps (leanback first). */
object SafeModeList {
    const val HEADER = "Safe mode — the main launcher crashed repeatedly"
    const val TITLE = "${Brand.SHORT} safe mode"
    /** Quiet last line of both safe-mode screens. */
    const val FOOTER = "${Brand.NAME} launcher, running in safe mode"

    /** One calm line under the title; the exception class name only, never a stack trace. */
    fun subtitle(lastError: String?, minimal: Boolean): String {
        val what = if (minimal) "The launcher and its safe mode closed unexpectedly." else "The launcher closed unexpectedly several times."
        val err = lastError?.trim()?.takeIf { it.isNotEmpty() }
        return if (err != null) "$what Last error: $err." else what
    }

    /** Fixed way-out rows read as actions (accent), apps as plain rows, notices as quiet text. */
    fun isAction(row: Row): Boolean = row is Row.TryNormal || row is Row.SystemSettings || row is Row.StockLauncher
    const val STOCK_LAUNCHER = "com.google.android.tvlauncher"
    const val APPS_FAILED = "Could not list apps"
    /** Shown when the stock launcher's own label cannot be read. */
    const val STOCK_LABEL_FALLBACK = "Android TV Home"

    /** How to re-enable the stock launcher; [stockLabel] is its label read at runtime. */
    fun enableStockHint(stockLabel: String): String =
        "To get it back: Settings → Apps → See all apps → Show system apps → $stockLabel → Enable"

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
        rows += Row.SystemSettings // first: default focus, the always-usable way out
        rows += Row.TryNormal
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
    fun minimal(stockLauncherAvailable: Boolean, stockLabel: String = STOCK_LABEL_FALLBACK): List<Row> = buildList {
        add(Row.SystemSettings)
        if (stockLauncherAvailable) add(Row.StockLauncher) else add(Row.Notice(enableStockHint(stockLabel)))
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
