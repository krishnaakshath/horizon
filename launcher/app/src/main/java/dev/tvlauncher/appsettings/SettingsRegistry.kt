package dev.tvlauncher.appsettings

/**
 * Ordered sidebar sections. Built-ins come first in a fixed order; [register] appends new ids
 * after them, replaces a same-id entry in place (so a module's real page takes its placeholder's
 * slot) and is idempotent per id. [shared] is the process-wide instance the activity reads.
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

    companion object {
        /** Ids of the built-in sections, in sidebar order. */
        val BUILT_IN_IDS = listOf(
            AppearanceSection.ID, HomeLayoutSection.ID, NetworkSection.ID,
            ModulePlaceholderSection.SOUNDS, ModulePlaceholderSection.INSIGHTS,
            ModulePlaceholderSection.HELPER, ModulePlaceholderSection.BROWSER, AboutSection.ID
        )

        fun newDefault(): SettingsRegistry = SettingsRegistry(
            listOf(
                AppearanceSection,
                HomeLayoutSection,
                NetworkSection,
                ModulePlaceholderSection.sounds(),
                ModulePlaceholderSection.insights(),
                ModulePlaceholderSection.helper(),
                ModulePlaceholderSection.browser(),
                AboutSection
            )
        )

        val shared: SettingsRegistry by lazy { newDefault() }

        /** Hook for other modules: add (or replace) a section in the shared registry. */
        fun register(section: SettingsSection) = shared.register(section)
    }
}
