package dev.tvlauncher.appsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every built-in page declares its rows; none may show more than [PageSpec.MAX_PRIMARY] at first. */
class PageBudgetTest {
    private fun builtIns(): List<SettingsSection> {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        dev.tvlauncher.setup.SetupSection.register(r)
        return r.sections().filter { it !is Slot }
    }

    @Test
    fun everyBuiltInPage_declaresASpec() {
        for (s in builtIns()) assertTrue("${s.id} has no PageSpec", s is SpecifiedPage)
    }

    @Test
    fun noBuiltInPage_exceedsThePrimaryRowBudget() {
        assertEquals(7, PageSpec.MAX_PRIMARY)
        for (s in builtIns()) {
            val spec = (s as SpecifiedPage).spec
            assertTrue("${s.id}: ${spec.primaryRows.size} primary rows", spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
            assertTrue("${s.id}: a page needs at least one row", spec.primaryRows.isNotEmpty())
        }
    }

    @Test
    fun rowKeys_areUniqueAndNamespacedByPage() {
        for (s in builtIns()) {
            val spec = (s as SpecifiedPage).spec
            val keys = spec.primaryRows + spec.advancedRows
            assertEquals(s.id, keys.size, keys.toSet().size)
            assertEquals(s.id, spec.id)
        }
    }

    @Test
    fun visibleRowsOnEntry_includeTheAdvancedToggle() {
        for (s in builtIns()) {
            val spec = (s as SpecifiedPage).spec
            val n = AdvancedToggle().visibleRows(spec.primaryRows.size, spec.advancedRows.size)
            assertTrue("${s.id}: $n rows", n <= PageSpec.MAX_PRIMARY + 1)
        }
    }

    @Test
    fun budget_failsForAnOverfullPage() {
        val fat = PageSpec("fat", (1..8).map { "fat.$it" })
        assertTrue(!fat.withinBudget)
        assertTrue(PageSpec("ok", (1..7).map { "ok.$it" }, listOf("ok.adv")).withinBudget)
    }
}
