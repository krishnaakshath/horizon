package dev.tvlauncher.system

import dev.tvlauncher.appsettings.SettingsRegistry

/**
 * Registers the launcher-owned system pages that are not built in. Wi-Fi & network, Bluetooth
 * and TV & system are built-ins of [SettingsRegistry.newDefault]; "devices" takes its
 * built-in slot; Accessibility, Parental controls and Apps are new. Each section is
 * registered exactly once (here or as a built-in).
 */
object SystemSections {
    fun registerAll(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(DevicesSettingsSection)
        registry.register(AccessibilitySection)
        registry.register(ParentalSection)
        registry.register(AppsSection)
    }
}
