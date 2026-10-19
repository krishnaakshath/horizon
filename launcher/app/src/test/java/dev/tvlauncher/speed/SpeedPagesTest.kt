package dev.tvlauncher.speed

import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.dns.PrivateDnsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two new pages keep the Settings rules: declared rows, within budget, in the right group. */
class SpeedPagesTest {
    private val specs = listOf(SpeedSection.spec, PrivateDnsSection.spec, PrivateDnsSection.PICKER_SPEC)

    @Test fun specs_withinBudget_uniqueAndNamespaced() {
        for (s in specs) {
            assertTrue(s.id, s.withinBudget)
            assertTrue(s.id, s.primaryRows.size <= PageSpec.MAX_PRIMARY)
            val keys = s.primaryRows + s.advancedRows
            assertEquals(s.id, keys.size, keys.toSet().size)
        }
        assertTrue(SpeedSection.spec.primaryRows.all { it.startsWith("speed.") })
        assertTrue((PrivateDnsSection.spec.primaryRows + PrivateDnsSection.PICKER_SPEC.primaryRows).all { it.startsWith("dns.") })
    }

    @Test fun rowGuard_acceptsEveryDeclaredRowOnce() {
        for (s in specs) {
            val g = RowGuard(s)
            s.primaryRows.forEach { g.claim(it, advanced = false) }
            s.advancedRows.forEach { g.claim(it, advanced = true) }
            assertEquals(s.primaryRows.size, g.primaryCount)
        }
    }

    @Test fun pickerHasOneRowPerChoice() {
        assertEquals(dev.tvlauncher.dns.PrivateDns.Choice.values().size, PrivateDnsSection.PICKER_SPEC.primaryRows.size)
    }

    @Test fun registration_placesPagesInTheirGroups() {
        val r = SettingsRegistry.newDefault()
        Speed.registerSections(r)
        Speed.registerSections(r) // idempotent
        val byId = r.sections().associateBy { it.id }
        assertEquals(SettingsGroup.SYSTEM, byId.getValue(SpeedSection.ID).group)
        assertEquals(SettingsGroup.CONNECTIONS, byId.getValue(PrivateDnsSection.ID).group)
        assertEquals(1, r.sections().count { it.id == SpeedSection.ID })
    }
}
