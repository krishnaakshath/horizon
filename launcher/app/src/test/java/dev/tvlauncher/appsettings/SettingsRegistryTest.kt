package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsRegistryTest {
    private class Fake(override val id: String, override val title: String = id) : SettingsSection {
        override val iconRes: Int = 0
        override fun buildPage(context: Context, nav: SettingsNav): View = throw UnsupportedOperationException()
    }

    private val builtInOrder = listOf(
        "appearance", "home_layout", "network", "bluetooth", "devices", "sounds",
        "browser", "getapps", "helper", "insights", "addons", "system", "backup", "about"
    )

    @Test
    fun registry_orderIsStable() {
        val a = SettingsRegistry.newDefault().sections().map { it.id }
        val b = SettingsRegistry.newDefault().sections().map { it.id }
        assertEquals(builtInOrder, a)
        assertEquals(a, b)
    }

    @Test
    fun registry_register_addsAfterBuiltIns() {
        val r = SettingsRegistry.newDefault()
        r.register(Fake("weather"))
        r.register(Fake("presence"))
        assertEquals(builtInOrder + listOf("weather", "presence"), r.sections().map { it.id })
    }

    @Test
    fun registry_register_replacesPlaceholderInPlace() {
        val r = SettingsRegistry.newDefault()
        val sounds = Fake("sounds", "Sounds")
        r.register(sounds)
        val list = r.sections()
        assertEquals(builtInOrder, list.map { it.id })
        assertSame(sounds, list[builtInOrder.indexOf("sounds")])
    }

    @Test
    fun registry_builtInIdsConstant_matchesDefault() {
        assertEquals(SettingsRegistry.BUILT_IN_IDS, SettingsRegistry.newDefault().sections().map { it.id })
    }

    @Test
    fun registry_register_sameIdTwice_keepsOneEntry() {
        val r = SettingsRegistry.newDefault()
        r.register(Fake("weather"))
        val second = Fake("weather", "Weather 2")
        r.register(second)
        assertEquals(builtInOrder + "weather", r.sections().map { it.id })
        assertSame(second, r.sections().last())
    }

    /** LauncherApp's registrations: every section id comes from exactly one place (a placeholder may be replaced once). */
    @Test
    fun launcherRegistrations_eachSectionRegisteredOnce() {
        fun ids(block: (SettingsRegistry) -> Unit) = SettingsRegistry(emptyList()).also(block).sections().map { it.id }
        val builtIns = SettingsRegistry.newDefault().sections().filter { it !is ModulePlaceholderSection }.map { it.id }
        val sources = listOf(
            builtIns,
            ids { ModuleSections.registerAll(it) },
            ids { dev.tvlauncher.home.WaveTwoSections.registerAll(it) },
            ids { dev.tvlauncher.setup.SetupSection.register(it) }.filter { it != BackupSection.ID }, // takes the built-in Backup slot
            ids { it.register(dev.tvlauncher.spotlight.SpotlightSection) },
            ids { dev.tvlauncher.system.SystemSections.registerAll(it) }
        )
        val all = sources.flatten()
        assertEquals(all.groupingBy { it }.eachCount().filterValues { it > 1 }, emptyMap<String, Int>())
    }
}
