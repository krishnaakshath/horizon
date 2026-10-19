package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupedRegistryTest {
    private class Fake(
        override val id: String,
        override val group: SettingsGroup = SettingsGroup.defaultFor(id),
        override val availability: SectionAvailability = SectionAvailability.Always
    ) : SettingsSection {
        override val title: String = id
        override val iconRes: Int = 0
        override fun buildPage(context: Context, nav: SettingsNav): View = throw UnsupportedOperationException()
    }

    /** A section written against the old API: no group, no availability. */
    private class Legacy(override val id: String) : SettingsSection {
        override val title: String = id
        override val iconRes: Int = 0
        override fun buildPage(context: Context, nav: SettingsNav): View = throw UnsupportedOperationException()
    }

    private class Probe(val apps: Set<String> = emptySet(), val classes: Set<String> = emptySet()) :
        SectionAvailability.Probe {
        override fun appInstalled(pkg: String) = pkg in apps
        override fun classPresent(name: String) = name in classes
    }

    private fun ids(groups: List<SectionGroup>) = groups.map { g -> g.group to g.sections.map { it.id } }

    @Test
    fun groups_followEnumOrder() {
        assertEquals(
            listOf(SettingsGroup.HOME, SettingsGroup.CONNECTIONS, SettingsGroup.SOUND, SettingsGroup.APPS, SettingsGroup.SYSTEM),
            SettingsGroup.values().toList()
        )
        assertEquals(listOf("Home", "Connections", "Sound", "Apps & tools", "System"), SettingsGroup.values().map { it.title })
    }

    @Test
    fun legacySection_defaultsToApps() {
        assertEquals(SettingsGroup.APPS, Legacy("weather").group)
        assertEquals(SectionAvailability.Always, Legacy("weather").availability)
        assertEquals(SettingsGroup.APPS, SettingsGroup.defaultFor("something-new"))
    }

    @Test
    fun wellKnownIds_landInTheirGroups_evenWithoutDeclaringOne() {
        assertEquals(SettingsGroup.CONNECTIONS, Legacy("devices").group)
        assertEquals(SettingsGroup.HOME, Legacy("ambient").group)
        assertEquals(SettingsGroup.HOME, Legacy("appearance").group)
        assertEquals(SettingsGroup.SYSTEM, Legacy("about").group)
    }

    @Test
    fun build_groupsInEnumOrder_entriesInRegistryOrder_emptyGroupsDropped() {
        val list = listOf(
            Fake("about"), Fake("x", SettingsGroup.APPS), Fake("appearance"), Fake("home_layout"),
            Fake("y", SettingsGroup.HOME)
        )
        val g = GroupedRegistry.build(list, Probe())
        assertEquals(
            listOf(
                SettingsGroup.HOME to listOf("appearance", "home_layout", "y"),
                SettingsGroup.APPS to listOf("x"),
                SettingsGroup.SYSTEM to listOf("about")
            ),
            ids(g)
        )
    }

    @Test
    fun build_hidesUninstalledModulesAndPlaceholders() {
        val list = listOf(
            Fake("browser", SettingsGroup.APPS, SectionAvailability.AppInstalled("dev.tvlauncher.browser")),
            Fake("helper", SettingsGroup.APPS, SectionAvailability.AppInstalled("dev.tvlauncher.helper")),
            Fake("devices", SettingsGroup.CONNECTIONS, SectionAvailability.Never),
            Fake("lab", SettingsGroup.APPS, SectionAvailability.ClassPresent("a.B"))
        )
        val none = GroupedRegistry.build(list, Probe())
        assertTrue(none.isEmpty())
        val some = GroupedRegistry.build(list, Probe(apps = setOf("dev.tvlauncher.helper"), classes = setOf("a.B")))
        assertEquals(listOf(SettingsGroup.APPS to listOf("helper", "lab")), ids(some))
    }

    @Test
    fun defaultRegistry_withNothingInstalled_hasNoPlaceholdersOrModulePages() {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        val visible = GroupedRegistry.build(r.sections(), Probe()).flatMap { it.sections }
        assertFalse(visible.any { it is Slot })
        val ids = visible.map { it.id }
        assertFalse("browser" in ids)
        assertFalse("helper" in ids)
        assertFalse(GetAppsSection.ID in ids)
        assertTrue(AddOnsSection.ID in ids)
    }

    @Test
    fun defaultRegistry_groupsMatchTheInformationArchitecture() {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        r.register(dev.tvlauncher.setup.ConfigBackupSection) // fills the "backup" slot, as in LauncherApp
        val all = setOf("dev.tvlauncher.browser", "dev.tvlauncher.helper", AddOns.STORE_PACKAGE)
        val g = GroupedRegistry.build(r.sections(), Probe(apps = all))
        assertEquals(
            listOf(
                SettingsGroup.HOME to listOf("appearance", "home_layout"),
                SettingsGroup.CONNECTIONS to listOf("network", "bluetooth"),
                SettingsGroup.SOUND to listOf("sounds"),
                SettingsGroup.APPS to listOf("browser", GetAppsSection.ID, "helper", "insights", AddOnsSection.ID),
                SettingsGroup.SYSTEM to listOf("system", "backup", "about")
            ),
            ids(g)
        )
    }

    @Test
    fun registeringDevicesLater_showsItUnderConnections() {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        r.register(Legacy("devices"))
        r.register(Fake("ambient", SettingsGroup.HOME))
        val g = GroupedRegistry.build(r.sections(), Probe())
        assertEquals(listOf("network", "bluetooth", "devices"), g.first { it.group == SettingsGroup.CONNECTIONS }.sections.map { it.id })
        assertEquals(listOf("appearance", "home_layout", "ambient"), g.first { it.group == SettingsGroup.HOME }.sections.map { it.id })
    }
}
