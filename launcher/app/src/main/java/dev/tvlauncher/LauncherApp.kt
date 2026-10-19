package dev.tvlauncher

import android.app.Application
import dev.tvlauncher.appsettings.ModuleSections
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.home.WaveTwoSections
import dev.tvlauncher.power.PowerOn
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
            PowerOn.install(this) // "Open home after power on": screen-on signal and home tracking
        } catch (e: Throwable) {
            // The manifest receiver still covers boot and TCL's wake broadcast.
        }
        try {
            Theme.installFonts(this) // Inter everywhere; preloaded off the main thread
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
            dev.tvlauncher.setup.SetupSection.register()
        } catch (e: Throwable) {
            // No Setup page in Settings this run.
        }
        try {
            SettingsRegistry.register(SpotlightSection) // Settings > Home > Spotlight
        } catch (e: Throwable) {
            // Spotlight keeps its defaults; only the page is missing.
        }
        try {
            // Launcher-owned system pages that are not built in (Devices, Accessibility,
            // Parental controls, Apps); last, so they take their placeholders' slots.
            dev.tvlauncher.system.SystemSections.registerAll()
        } catch (e: Throwable) {
            // Devices keeps its placeholder; the other pages are missing this run.
        }
    }
}
