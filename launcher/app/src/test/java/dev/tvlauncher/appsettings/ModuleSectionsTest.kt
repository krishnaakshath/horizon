package dev.tvlauncher.appsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSectionsTest {
    @Test
    fun registerAll_replacesPlaceholdersInPlace() {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        val ids = r.sections().map { it.id }
        assertEquals(SettingsRegistry.BUILT_IN_IDS, ids)
        for (id in ModuleSections.IDS) {
            assertFalse(id, r.sections().first { it.id == id } is ModulePlaceholderSection)
        }
    }

    @Test
    fun registerAll_isIdempotent() {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        val first = r.sections()
        ModuleSections.registerAll(r)
        assertEquals(first.map { it.id }, r.sections().map { it.id })
    }

    @Test
    fun usageAccessStatus_showsTheHelperLineOnlyWhenMissing() {
        val granted = ModuleSections.usageAccessStatus(granted = true)
        assertEquals("Granted", granted.first)
        assertNull(granted.second)
        val missing = ModuleSections.usageAccessStatus(granted = false)
        assertEquals("Not granted", missing.first)
        assertEquals(dev.tvlauncher.setup.SetupCopy.HELPER_LINE, missing.second)
    }

    @Test
    fun installedStatus() {
        assertEquals("Not installed", ModuleSections.installedValue(false))
        assertEquals("Installed", ModuleSections.installedValue(true))
    }

    @Test
    fun helperSection_isRegistered_andTargetsHelperSettings() {
        assertTrue(ModulePlaceholderSection.HELPER in ModuleSections.IDS)
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        val helper = r.sections().first { it.id == ModulePlaceholderSection.HELPER }
        assertEquals("Helper", helper.title)
        assertEquals("dev.tvlauncher.helper", ModuleSections.HELPER_PACKAGE)
        assertEquals("dev.tvlauncher.helper.HelperSettingsActivity", ModuleSections.HELPER_ACTIVITY)
    }
}
