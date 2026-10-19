package dev.tvlauncher.appsettings

import dev.tvlauncher.dns.PrivateDnsSection
import dev.tvlauncher.home.WaveTwoSections
import dev.tvlauncher.setup.SetupSection
import dev.tvlauncher.speed.Speed
import dev.tvlauncher.speed.SpeedSection
import dev.tvlauncher.spotlight.SpotlightSection
import dev.tvlauncher.system.SystemSections
import dev.tvlauncher.volume.VolumeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every module registered in LauncherApp's order: each section once, in a sensible place, within budget. */
class LauncherRegistrationTest {
    private fun all(): SettingsRegistry {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        Speed.registerSections(r)
        VolumeSection.register(r)
        WaveTwoSections.registerAll(r)
        SetupSection.register(r)
        r.register(SpotlightSection)
        SystemSections.registerAll(r)
        return r
    }

    @Test fun everySectionIsRegisteredOnce() {
        val ids = all().sections().map { it.id }
        assertEquals(ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toString(), ids.size, ids.toSet().size)
    }

    @Test fun soundGroup_listsVolumeAndOutputAboveUiSounds() {
        val sound = all().sections().filter { it.group == SettingsGroup.SOUND }.map { it.id }
        assertEquals(listOf(VolumeSection.ID, ModulePlaceholderSection.SOUNDS), sound)
    }

    @Test fun speedAndPrivateDns_sitInTheirGroups() {
        val byId = all().sections().associateBy { it.id }
        assertEquals(SettingsGroup.SYSTEM, byId.getValue(SpeedSection.ID).group)
        assertEquals(SettingsGroup.CONNECTIONS, byId.getValue(PrivateDnsSection.ID).group)
    }

    @Test fun everySpecifiedPage_isWithinThePrimaryRowBudget() {
        for (s in all().sections().filterIsInstance<SpecifiedPage>()) {
            assertTrue("${s.spec.id}: ${s.spec.primaryRows.size} primary rows", s.spec.withinBudget)
        }
        assertTrue(PrivateDnsSection.PICKER_SPEC.withinBudget)
    }
}
