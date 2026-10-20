package dev.tvlauncher

import android.app.Application
import dev.tvlauncher.appsettings.ModuleSections
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.home.WaveTwoSections
import dev.tvlauncher.power.PowerOn
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.speed.Speed
import dev.tvlauncher.spotlight.SpotlightSection
import dev.tvlauncher.ui.Theme

/**
 * Process start: installs the crash guard first, then registers the module Settings pages.
 * Nothing here may stop the process from starting, so every step swallows any Throwable.
 */
class LauncherApp : Application(), dev.tvlauncher.profiles.ProfileStores.RawPrefs {
    /** Per-person files (favorites, look, wallpaper, ...) open the active profile's copy (profiles.ProfileStores). */
    override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences =
        super.getSharedPreferences(name?.let { dev.tvlauncher.profiles.ProfileStores.route(it) }, mode)

    override fun rawSharedPreferences(name: String): android.content.SharedPreferences =
        super.getSharedPreferences(name, MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        try {
            dev.tvlauncher.profiles.ProfileManager.install(this) // before anything reads a per-person file
        } catch (e: Throwable) {
            // Home's files (the original ones) this run.
        }
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
            // Their slots stay hidden in Settings; the home screen is unaffected.
        }
        try {
            Speed.install(this) // Settings > System > Speed and Connections > Private DNS; nightly cleanup
        } catch (e: Throwable) {
            // No Speed / Private DNS pages and no automatic cleanup this run.
        }
        try {
            dev.tvlauncher.volume.VolumeSection.register() // Settings > Sound > Volume & output
        } catch (e: Throwable) {
            // No Volume & output page this run; TV & system keeps its volume rows.
        }
        try {
            dev.tvlauncher.controllers.ControllersSection.register() // Settings > System > Controllers
        } catch (e: Throwable) {
            // No Controllers page this run; controllers still work with the default mapping.
        }
        try {
            WaveTwoSections.registerAll()
        } catch (e: Throwable) {
            // Same: the wave-2 pages are absent.
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
            dev.tvlauncher.watch.WatchSection.register() // Settings > Home > Continue watching
        } catch (e: Throwable) {
            // The row keeps its defaults; only the page is missing.
        }
        try {
            // Settings > System > Recent apps, Focus mode and Remote.
            dev.tvlauncher.switcher.SwitcherSection.register()
            dev.tvlauncher.focus.FocusSection.register()
            dev.tvlauncher.remotecheck.RemoteCheckSection.register()
        } catch (e: Throwable) {
            // Those pages are missing this run; the home screen is unaffected.
        }
        try {
            // Launcher-owned system pages that are not built in (Devices, Accessibility,
            // Parental controls, Apps); last, so Devices fills its built-in slot.
            dev.tvlauncher.system.SystemSections.registerAll()
        } catch (e: Throwable) {
            // Devices stays a hidden slot; the other pages are missing this run.
        }
        try {
            dev.tvlauncher.profiles.ProfilesSection.register() // Settings > System > Profiles
            dev.tvlauncher.routines.RoutinesSection.register() // Settings > System > Routines
        } catch (e: Throwable) {
            // No Profiles / Routines pages this run; Home keeps the active profile.
        }
        try {
            dev.tvlauncher.link.ForegroundTracker.install(this) // phone remote: which of our screens is in front
            dev.tvlauncher.link.PhoneRemoteSection.register() // Settings > System > Phone remote
        } catch (e: Throwable) {
            // The phone remote answers "not available" and its page is missing this run.
        }
    }
}
