package dev.tvlauncher

import android.app.Application
import dev.tvlauncher.appsettings.ModuleSections
import dev.tvlauncher.safemode.SafeMode

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
            ModuleSections.registerAll()
        } catch (e: Throwable) {
            // Settings then shows the placeholders; the home screen is unaffected.
        }
    }
}
