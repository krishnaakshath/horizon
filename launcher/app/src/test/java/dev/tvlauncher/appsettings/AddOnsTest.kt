package dev.tvlauncher.appsettings

import dev.tvlauncher.ambient.WeatherContract
import dev.tvlauncher.ui.QuickActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddOnsTest {
    @Test
    fun listsTheFourModules_inOrder_withTheirPackages() {
        assertEquals(listOf("Browser", "Get Apps", "Weather", "Helper"), AddOns.all.map { it.title })
        assertEquals(
            listOf(QuickActions.BROWSER_PACKAGE, AddOns.STORE_PACKAGE, WeatherContract.PACKAGE, ModuleSections.HELPER_PACKAGE),
            AddOns.all.map { it.packageName }
        )
        assertEquals("dev.tvlauncher.store", AddOns.STORE_PACKAGE)
    }

    @Test
    fun everyAddOn_saysWhatItAdds_inOneShortLine() {
        for (a in AddOns.all) assertTrue(a.title, a.adds.isNotBlank() && a.adds.length <= 60 && '\n' !in a.adds)
    }

    @Test
    fun statusChip_andThePrimaryAction() {
        assertEquals("Installed ✓", AddOns.chip(true))
        assertEquals("Not installed", AddOns.chip(false))
        assertEquals("Open", AddOns.primaryAction(true))
        assertEquals("How to install", AddOns.primaryAction(false))
    }

    @Test
    fun howToInstall_isFriendly_noDeveloperCommands() {
        for (a in AddOns.all) {
            val t = AddOns.howToInstall(a)
            assertTrue(t.contains(a.title))
            assertTrue(!t.contains("adb") && !t.contains(a.packageName))
        }
    }

    @Test
    fun moduleSections_pointAtTheirAddOn() {
        assertEquals("browser", AddOns.byId("browser")?.sectionId)
        assertEquals(GetAppsSection.ID, AddOns.byId("getapps")?.sectionId)
        assertEquals("helper", AddOns.byId("helper")?.sectionId)
    }
}
