package dev.tvlauncher.appsettings

/**
 * Ordered settings sections. Built-ins come first in a fixed order; [register] appends new ids
 * after them, replaces a same-id entry in place (so a module's real page takes its placeholder's
 * slot) and is idempotent per id. [shared] is the process-wide instance the activity reads.
 *
 * The sidebar shows these grouped ([GroupedRegistry]): by [SettingsSection.group] in
 * [SettingsGroup] order, and within a group in this registry order, so a section registered
 * later lands at the end of its group.
 */
class SettingsRegistry(builtIns: List<SettingsSection>) {
    private val entries = ArrayList(builtIns)

    @Synchronized
    fun register(section: SettingsSection) {
        val i = entries.indexOfFirst { it.id == section.id }
        if (i >= 0) entries[i] = section else entries.add(section)
    }

    @Synchronized
    fun sections(): List<SettingsSection> = entries.toList()

    /** The visible sidebar groups for [probe] (installed apps, compiled-in classes). */
    fun groups(probe: SectionAvailability.Probe): List<SectionGroup> = GroupedRegistry.build(sections(), probe)

    companion object {
        /** Ids of the built-in sections, in registry order. */
        val BUILT_IN_IDS = listOf(
            AppearanceSection.ID, HomeLayoutSection.ID,
            NetworkSection.ID, BluetoothSection.ID, ModulePlaceholderSection.DEVICES,
            ModulePlaceholderSection.SOUNDS,
            ModulePlaceholderSection.BROWSER, GetAppsSection.ID, ModulePlaceholderSection.HELPER,
            ModulePlaceholderSection.INSIGHTS, AddOnsSection.ID,
            SystemSection.ID, BackupSection.ID, AboutSection.ID
        )

        fun newDefault(): SettingsRegistry = SettingsRegistry(
            listOf(
                AppearanceSection,
                HomeLayoutSection,
                NetworkSection,
                BluetoothSection,
                ModulePlaceholderSection.devices(),
                ModulePlaceholderSection.sounds(),
                ModulePlaceholderSection.browser(),
                GetAppsSection,
                ModulePlaceholderSection.helper(),
                ModulePlaceholderSection.insights(),
                AddOnsSection,
                SystemSection,
                BackupSection,
                AboutSection
            )
        )

        val shared: SettingsRegistry by lazy { newDefault() }

        /** Hook for other modules: add (or replace) a section in the shared registry. */
        fun register(section: SettingsSection) = shared.register(section)
    }
}
