package dev.tvlauncher.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The only door from the launcher into the TV's own settings screens, for the few things an app
 * cannot do (see docs/SYSTEM_SETTINGS.md). Every screen has a specific target first and the TV's
 * main settings last; each step is guarded. Safe mode keeps its own escape ([dev.tvlauncher.safemode.SettingsEscape]).
 */
object TvScreens {
    enum class Screen(val title: String, val why: String) {
        PICTURE("Picture & sound", "Picture modes, backlight and sound processing are the TV's own"),
        DISPLAY("Resolution & screen", "Output resolution and screen position need system access"),
        ACCESSIBILITY("Accessibility services", "Turning services on needs the TV's screen or a one-time grant"),
        UPDATE("System update", "Only the TV can install its own updates"),
        RESET("Factory reset", "Erasing the TV needs system access"),
        LOCATION("Location", "Only the TV can switch Location on or off"),
        ALL("All TV settings", "Everything else the TV offers");
    }

    sealed class Target {
        data class Action(val action: String) : Target()
        data class Pkg(val pkg: String) : Target()
    }

    const val TCL_SETTINGS = "com.tcl.settings"
    const val TV_SETTINGS = "com.android.tv.settings"
    private const val ACTION_SETTINGS = "android.settings.SETTINGS"

    /** Specific screen first, the TV's main settings last. Pure: ordered targets. */
    fun chain(screen: Screen): List<Target> {
        val specific = when (screen) {
            // TCL keeps picture/sound/resolution in its own settings app.
            Screen.PICTURE, Screen.DISPLAY -> listOf(Target.Pkg(TCL_SETTINGS), Target.Action("android.settings.DISPLAY_SETTINGS"))
            Screen.ACCESSIBILITY -> listOf(Target.Action("android.settings.ACCESSIBILITY_SETTINGS"))
            Screen.UPDATE -> listOf(Target.Action("android.settings.SYSTEM_UPDATE_SETTINGS"), Target.Action("android.settings.DEVICE_INFO_SETTINGS"))
            Screen.RESET -> listOf(Target.Action("android.settings.DEVICE_INFO_SETTINGS"))
            Screen.LOCATION -> listOf(Target.Action("android.settings.LOCATION_SOURCE_SETTINGS"))
            Screen.ALL -> emptyList()
        }
        return specific + Target.Action(ACTION_SETTINGS) + Target.Pkg(TV_SETTINGS)
    }

    fun open(context: Context, screen: Screen): Boolean {
        val pm = context.packageManager
        for (t in chain(screen)) {
            val i = safe<Intent?>(null) {
                when (t) {
                    is Target.Action -> Intent(t.action).takeIf { pm.resolveActivity(it, 0) != null }
                    is Target.Pkg -> pm.getLeanbackLaunchIntentForPackage(t.pkg) ?: pm.getLaunchIntentForPackage(t.pkg)
                }
            } ?: continue
            val ok = safe(false) { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
            if (ok) return true
        }
        return false
    }
}

/** Runtime permissions are still asked for properly (targetSdk 28 > 23, nothing is auto-granted). */
object SystemPermissions {
    fun hasLocation(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows the system's permission dialog (a dialog, not the settings app), through setup's own
     * asker ([dev.tvlauncher.setup.SetupStatus.requestPermission]) so setup and these panels agree on
     * what was already asked. Once Android stops showing the dialog it opens the launcher's own
     * app page instead. False when nothing could open.
     */
    fun requestLocation(context: Context): Boolean {
        if (hasLocation(context)) return true
        return dev.tvlauncher.setup.SetupStatus.requestPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** WRITE_SECURE_SETTINGS can only be granted once from the provisioning tool; the launcher never asks. */
    fun canWriteSecure(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
}
