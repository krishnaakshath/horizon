package dev.tvlauncher.safemode

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * Debuggable-build-only hooks to exercise safe mode on the TV, handled by HomeActivity (onCreate
 * and onNewIntent). Checked with ApplicationInfo.FLAG_DEBUGGABLE at runtime; a release build
 * ignores these actions. The only extra read is `count`, clamped to 1..10.
 *
 * ```
 * # Record 3 home crashes now (trips safe mode) and redirect:
 * adb shell am start -n dev.tvlauncher/.HomeActivity -a dev.tvlauncher.debug.TRIP_SAFE_MODE
 * adb shell am start -n dev.tvlauncher/.HomeActivity -a dev.tvlauncher.debug.TRIP_SAFE_MODE --ei count 2
 * # Clear the crash history and trip:
 * adb shell am start -n dev.tvlauncher/.HomeActivity -a dev.tvlauncher.debug.RESET_SAFE_MODE
 * # Make the home screen really crash (counts like a real crash; run it 3 times within 3 minutes):
 * adb shell am start -n dev.tvlauncher/.HomeActivity -a dev.tvlauncher.debug.CRASH_HOME
 * ```
 */
object DebugTriggers {
    const val TRIP = "dev.tvlauncher.debug.TRIP_SAFE_MODE"
    const val RESET = "dev.tvlauncher.debug.RESET_SAFE_MODE"
    const val CRASH_HOME = "dev.tvlauncher.debug.CRASH_HOME"
    const val EXTRA_COUNT = "count"

    sealed class Command {
        data class Trip(val count: Int) : Command()
        object Reset : Command()
        object CrashHome : Command()
    }

    fun decide(action: String?, debuggable: Boolean, count: Int?): Command? {
        if (!debuggable) return null
        return when (action) {
            TRIP -> Command.Trip((count ?: 3).coerceIn(1, 10))
            RESET -> Command.Reset
            CRASH_HOME -> Command.CrashHome
            else -> null
        }
    }

    /** Runs a debug command from [intent]; true when one was handled (the caller then checks the redirect). */
    fun handle(activity: Activity, intent: Intent?): Boolean {
        val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val count = intent?.takeIf { it.hasExtra(EXTRA_COUNT) }?.getIntExtra(EXTRA_COUNT, 3)
        val cmd = decide(intent?.action, debuggable, count) ?: return false
        activity.intent = Intent(intent).setAction(null) // a recreation must not repeat it
        val guard = SafeMode.guard(activity)
        when (cmd) {
            is Command.Trip -> repeat(cmd.count) { guard.recordCrash("DebugTrip") }
            Command.Reset -> guard.reset()
            Command.CrashHome -> throw IllegalStateException("Debug crash requested with $CRASH_HOME")
        }
        return true
    }
}
