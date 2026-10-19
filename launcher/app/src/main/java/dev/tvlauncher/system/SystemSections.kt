package dev.tvlauncher.system

import dev.tvlauncher.appsettings.SettingsRegistry

/**
 * Registers the launcher-owned system pages. Same-id sections replace the old shortcut pages in
 * place ("network", "bluetooth", "system", "devices"); the rest are new. Call after the other
 * modules registered theirs (LauncherApp), so these win.
 */
object SystemSections {
    fun registerAll(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(WifiSettingsSection)
        registry.register(BluetoothSettingsSection)
        registry.register(DevicesSettingsSection)
        registry.register(SystemHubSection)
        registry.register(AccessibilitySection)
        registry.register(ParentalSection)
        registry.register(AppsSection)
    }
}
