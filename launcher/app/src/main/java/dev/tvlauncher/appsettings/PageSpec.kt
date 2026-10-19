package dev.tvlauncher.appsettings

/**
 * Declared rows of a built-in page (row keys, in order). The page builders use the same keys,
 * and PageBudgetTest fails when a page shows more than [MAX_PRIMARY] rows before "Advanced".
 */
data class PageSpec(val id: String, val primaryRows: List<String>, val advancedRows: List<String> = emptyList()) {
    val withinBudget: Boolean get() = primaryRows.size <= MAX_PRIMARY

    companion object {
        const val MAX_PRIMARY = 7
    }
}

/** A section that declares its rows. */
interface SpecifiedPage {
    val spec: PageSpec
}
