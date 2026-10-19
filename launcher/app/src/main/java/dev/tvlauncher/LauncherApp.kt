package dev.tvlauncher

import android.app.Application
import dev.tvlauncher.appsettings.ModuleSections
import dev.tvlauncher.safemode.SafeMode

/** Process start: installs the crash guard, then registers the module Settings pages. */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SafeMode.install(this)
        try {
            ModuleSections.registerAll()
        } catch (e: RuntimeException) {
            // Settings then shows the placeholders; the home screen is unaffected.
        }
    }
}
