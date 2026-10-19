package dev.tvlauncher

import android.app.Application
import dev.tvlauncher.appsettings.ModuleSections

/** Process start: registers the module Settings pages before any screen can open. */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            ModuleSections.registerAll()
        } catch (e: RuntimeException) {
            // Settings then shows the placeholders; the home screen is unaffected.
        }
    }
}
