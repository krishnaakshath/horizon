package dev.tvlauncher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import dev.tvlauncher.safemode.SafeMode

/**
 * Debug-build-only hooks to exercise safe mode on the TV. This file and its manifest entry live in
 * src/debug only, so a release build contains neither. The receiver is protected by
 * android.permission.DUMP (the adb shell holds it, ordinary apps do not) and also checks
 * ApplicationInfo.FLAG_DEBUGGABLE. The only extra read is `count` (an int, clamped to 1..10).
 *
 * ```
 * # Record 3 home crashes (trips safe mode), then restart the process so HOME redirects:
 * adb shell am broadcast -a dev.tvlauncher.debug.TRIP_SAFE_MODE -n dev.tvlauncher/.debug.DebugReceiver --ei count 3
 * # Clear everything in the ladder (crash history, trip, safe-mode crashes, Settings escape):
 * adb shell am broadcast -a dev.tvlauncher.debug.RESET_SAFE_MODE -n dev.tvlauncher/.debug.DebugReceiver
 * # Crash the main thread; counts as a home crash while the home screen is in front:
 * adb shell am broadcast -a dev.tvlauncher.debug.CRASH_HOME -n dev.tvlauncher/.debug.DebugReceiver
 * ```
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val action = intent?.action
        if (!debuggable || DebugTriggers.decide(action, true, null) == null) return
        // Only now look at extras, defensively: anything malformed is ignored.
        val raw = try { @Suppress("DEPRECATION") intent?.extras?.get(DebugTriggers.EXTRA_COUNT) } catch (e: Throwable) { null }
        val cmd = DebugTriggers.decide(action, debuggable, DebugTriggers.parseCount(raw)) ?: return
        val guard = SafeMode.guard(context)
        val main = Handler(Looper.getMainLooper())
        when (cmd) {
            is DebugTriggers.Command.Trip -> {
                repeat(cmd.count) { guard.recordCrash("DebugTrip") }
                // Like a real crash: the process goes away and the system restarts the home screen.
                main.postDelayed({ Process.killProcess(Process.myPid()) }, 300)
            }
            DebugTriggers.Command.Reset -> guard.clearAll() // everything, incl. safe-mode crashes and the Settings escape
            DebugTriggers.Command.CrashHome -> main.post { throw IllegalStateException("Debug crash requested with ${DebugTriggers.CRASH_HOME}") }
        }
    }
}

/** Pure decisions for [DebugReceiver]. */
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

    /** Only a real Int is accepted; anything else (missing, string, long) means "not given". */
    fun parseCount(raw: Any?): Int? = raw as? Int

    fun decide(action: String?, debuggable: Boolean, count: Int?): Command? {
        if (!debuggable) return null
        return when (action) {
            TRIP -> Command.Trip((count ?: 3).coerceIn(1, 10))
            RESET -> Command.Reset
            CRASH_HOME -> Command.CrashHome
            else -> null
        }
    }
}
