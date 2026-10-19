package dev.tvlauncher.appsettings

/**
 * The five sidebar groups, in display order. A section picks its group with
 * [SettingsSection.group]; sections that do not declare one land in [APPS] (except a few
 * well-known ids, see [defaultFor]).
 */
enum class SettingsGroup(val title: String) {
    HOME("Home"),
    CONNECTIONS("Connections"),
    SOUND("Sound"),
    APPS("Apps & tools"),
    SYSTEM("System");

    companion object {
        /** Section ids (built-in or registered by other modules) with a fixed home. */
        private val KNOWN = mapOf(
            "appearance" to HOME, "home_layout" to HOME, "ambient" to HOME, "wallpaper" to HOME, "room_lighting" to HOME,
            "network" to CONNECTIONS, "bluetooth" to CONNECTIONS, "devices" to CONNECTIONS,
            "sounds" to SOUND,
            "system" to SYSTEM, "backup" to SYSTEM, "about" to SYSTEM
        )

        /** The group of a section that does not declare one: well-known ids keep their place, others are [APPS]. */
        fun defaultFor(id: String): SettingsGroup = KNOWN[id] ?: APPS
    }
}

/** Whether a section shows in the sidebar. Resolved against a [Probe] so the rule is JVM-testable. */
sealed class SectionAvailability {
    abstract fun resolve(probe: Probe): Boolean

    interface Probe {
        fun appInstalled(pkg: String): Boolean
        fun classPresent(name: String): Boolean
    }

    object Always : SectionAvailability() {
        override fun resolve(probe: Probe) = true
    }

    /** A slot keeper (a module placeholder): never listed; the module's real page replaces it. */
    object Never : SectionAvailability() {
        override fun resolve(probe: Probe) = false
    }

    /** A companion app; until it is installed it is listed only on the Add-ons page. */
    data class AppInstalled(val packageName: String) : SectionAvailability() {
        override fun resolve(probe: Probe) = probe.appInstalled(packageName)
    }

    data class ClassPresent(val className: String) : SectionAvailability() {
        override fun resolve(probe: Probe) = probe.classPresent(className)
    }
}

/** One sidebar group with its visible sections, in registry order. */
data class SectionGroup(val group: SettingsGroup, val sections: List<SettingsSection>)

object GroupedRegistry {
    /** Groups in [SettingsGroup] order; unavailable sections and empty groups are dropped. */
    fun build(sections: List<SettingsSection>, probe: SectionAvailability.Probe): List<SectionGroup> {
        val visible = sections.filter { it.availability.resolve(probe) }
        return SettingsGroup.values().mapNotNull { g ->
            val list = visible.filter { it.group == g }
            if (list.isEmpty()) null else SectionGroup(g, list)
        }
    }
}
