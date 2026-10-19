package dev.tvlauncher

import android.app.Application
import dev.tvlauncher.appsettings.ModuleSections
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.home.WaveTwoSections
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.spotlight.SpotlightSection
import dev.tvlauncher.ui.Theme

/**
 * Process start: installs the crash guard first, then registers the module Settings pages.
 * Nothing here may stop the process from starting, so every step swallows any Throwable.
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            SafeMode.install(this)
        } catch (e: Throwable) {
            // No guard this run; the home screen still starts.
        }
        try {
            Theme.installFonts(this) // Manrope everywhere; preloaded off the main thread
        } catch (e: Throwable) {
            // System sans instead.
        }
        try {
            ModuleSections.registerAll()
        } catch (e: Throwable) {
            // Settings then shows the placeholders; the home screen is unaffected.
        }
        try {
            WaveTwoSections.registerAll()
        } catch (e: Throwable) {
            // Same: the wave-2 pages stay placeholders or absent.
        }
        try {
            SettingsRegistry.register(SpotlightSection) // Settings > Home > Spotlight
        } catch (e: Throwable) {
            // Spotlight keeps its defaults; only the page is missing.
        }
    }
}
