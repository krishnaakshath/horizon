package dev.tvlauncher.safemode

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens the TV's own Settings app, the ladder's guaranteed end point. Framework only: no launcher
 * activity, theme, font, prefs or appearance code. Each target is tried in order, each guarded.
 */
object SettingsEscape {
    sealed class Target {
        data class Action(val action: String) : Target()
        /** Launch intent of a settings package (Android TV Settings). */
        data class Package(val pkg: String) : Target()
    }

    val TARGETS: List<Target> = listOf(
        Target.Action(Settings.ACTION_SETTINGS),
        Target.Action(Settings.ACTION_APPLICATION_SETTINGS),
        Target.Package("com.android.tv.settings")
    )

    /** True when one of [TARGETS] started. Never throws. */
    fun open(context: Context): Boolean {
        for (t in TARGETS) {
            try {
                val i = when (t) {
                    is Target.Action -> Intent(t.action)
                    is Target.Package -> context.packageManager.getLaunchIntentForPackage(t.pkg)
                        ?: context.packageManager.getLeanbackLaunchIntentForPackage(t.pkg)
                } ?: continue
                if (context.packageManager.resolveActivity(i, 0) == null) continue
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Throwable) {
                // next target
            }
        }
        return false
    }
}
