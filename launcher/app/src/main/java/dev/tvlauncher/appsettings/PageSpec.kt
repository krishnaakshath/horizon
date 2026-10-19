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

/**
 * Keeps a page honest while it is built: a [SettingsPage] created with a spec accepts only rows
 * whose keys are declared there (primary rows outside "Advanced", advanced rows inside it), each
 * once, and never more than [PageSpec.MAX_PRIMARY] primary rows. A violation throws, and the
 * Settings screen shows "This page could not be shown" instead of an overfull page.
 */
class RowGuard(val spec: PageSpec) {
    private val used = HashSet<String>()

    var primaryCount = 0
        private set

    fun claim(key: String, advanced: Boolean) {
        val declared = if (advanced) spec.advancedRows else spec.primaryRows
        require(key in declared) {
            "${spec.id}: row '$key' is not declared as ${if (advanced) "an advanced" else "a primary"} row"
        }
        require(used.add(key)) { "${spec.id}: row '$key' added twice" }
        if (!advanced) {
            primaryCount++
            check(primaryCount <= PageSpec.MAX_PRIMARY) { "${spec.id}: more than ${PageSpec.MAX_PRIMARY} primary rows" }
        }
    }
}
