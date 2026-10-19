package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import dev.tvlauncher.system.BluetoothSettingsSection
import dev.tvlauncher.system.SystemHubSection
import dev.tvlauncher.system.SystemSpecs
import dev.tvlauncher.system.WifiSettingsSection

/**
 * Ordered settings sections. Built-ins come first in a fixed order; [register] appends new ids
 * after them, replaces a same-id entry in place (so a module's real page takes its [Slot]) and is
 * idempotent per id. [shared] is the process-wide instance the activity reads.
 *
 * The sidebar shows these grouped ([GroupedRegistry]): by [SettingsSection.group] in
 * [SettingsGroup] order, and within a group in this registry order, so a section registered
 * later lands at the end of its group.
 *
 * For modules registering pages (e.g. WaveTwoSections): set [SettingsSection.group] explicitly.
 * Ambient/wallpaper go to [SettingsGroup.HOME] and Devices ("devices") to
 * [SettingsGroup.CONNECTIONS]; a Weather page belongs to [SettingsGroup.APPS] (override
 * `group = SettingsGroup.APPS`, which is also the default for the id "weather"). The Add-ons page
 * lists the Weather companion on its own and does not depend on that section.
 */
class SettingsRegistry(builtIns: List<SettingsSection>) {
    private val entries = ArrayList(builtIns)

    @Synchronized
    fun register(section: SettingsSection) {
        val i = entries.indexOfFirst { it.id == section.id }
        if (i >= 0) entries[i] = section else entries.add(section)
    }

    /** Like [register], but a new id goes right before [beforeId] (appended when that id is absent). */
    @Synchronized
    fun registerBefore(section: SettingsSection, beforeId: String) {
        val i = entries.indexOfFirst { it.id == section.id }
        if (i >= 0) { entries[i] = section; return }
        val at = entries.indexOfFirst { it.id == beforeId }
        if (at >= 0) entries.add(at, section) else entries.add(section)
    }

    @Synchronized
    fun sections(): List<SettingsSection> = entries.toList()

    /** The visible sidebar groups for [probe] (installed apps, compiled-in classes). */
    fun groups(probe: SectionAvailability.Probe): List<SectionGroup> = GroupedRegistry.build(sections(), probe)

    companion object {
        /** Ids of the built-in sections, in registry order. */
        val BUILT_IN_IDS = listOf(
            AppearanceSection.ID, HomeLayoutSection.ID,
            SystemSpecs.NETWORK, SystemSpecs.BLUETOOTH, SectionIds.DEVICES,
            SectionIds.SOUNDS,
            SectionIds.BROWSER, GetAppsSection.ID, SectionIds.HELPER,
            SectionIds.INSIGHTS, AddOnsSection.ID,
            SystemSpecs.SYSTEM, SectionIds.BACKUP, AboutSection.ID
        )

        fun newDefault(): SettingsRegistry = SettingsRegistry(
            listOf(
                AppearanceSection,
                HomeLayoutSection,
                WifiSettingsSection,
                BluetoothSettingsSection,
                Slot(SectionIds.DEVICES),
                Slot(SectionIds.SOUNDS),
                Slot(SectionIds.BROWSER),
                GetAppsSection,
                Slot(SectionIds.HELPER),
                Slot(SectionIds.INSIGHTS),
                AddOnsSection,
                SystemHubSection,
                Slot(SectionIds.BACKUP),
                AboutSection
            )
        )

        val shared: SettingsRegistry by lazy { newDefault() }

        /** Hook for other modules: add (or replace) a section in the shared registry. */
        fun register(section: SettingsSection) = shared.register(section)
    }
}

/**
 * Keeps a built-in id's place until its page registers (LauncherApp: ModuleSections, SetupSection,
 * SystemSections). Never listed in the sidebar or Spotlight ([SectionAvailability.Never]).
 */
class Slot(override val id: String) : SettingsSection {
    override val title: String get() = id
    override val iconRes: Int get() = 0
    override val availability: SectionAvailability get() = SectionAvailability.Never
    override fun buildPage(context: Context, nav: SettingsNav): View = SettingsPage(context, nav.style)
}

/** Ids of the built-in slots that a module's page fills ([SettingsRegistry.BUILT_IN_IDS]). */
object SectionIds {
    const val DEVICES = "devices"
    const val SOUNDS = "sounds"
    const val BROWSER = "browser"
    const val HELPER = "helper"
    const val INSIGHTS = "insights"
    const val BACKUP = "backup"
}
