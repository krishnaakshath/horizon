package dev.tvlauncher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import dev.tvlauncher.appsettings.AppSettingsActivity
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
 * # Open the launcher's Settings on a named page (the layout audit uses this, so its key presses
 * # always start from a known page instead of whatever page was open last):
 * adb shell am broadcast -a dev.tvlauncher.debug.OPEN_SETTINGS -n dev.tvlauncher/.debug.DebugReceiver --es section home_layout
 * ```
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val action = intent?.action
        if (!debuggable || !DebugTriggers.knows(action)) return
        // Only now look at extras, defensively: anything malformed is ignored.
        val raw = try { @Suppress("DEPRECATION") intent?.extras?.get(DebugTriggers.EXTRA_COUNT) } catch (e: Throwable) { null }
        val section = try { @Suppress("DEPRECATION") intent?.extras?.get(DebugTriggers.EXTRA_SECTION) } catch (e: Throwable) { null }
        val cmd = DebugTriggers.decide(action, debuggable, DebugTriggers.parseCount(raw), DebugTriggers.parseSection(section)) ?: return
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
            is DebugTriggers.Command.OpenSettings -> try {
                // A fresh instance: no saved state, so the requested page wins over the last one.
                context.startActivity(Intent(context, AppSettingsActivity::class.java)
                    .putExtra(AppSettingsActivity.EXTRA_SECTION, cmd.section)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Throwable) {
            }
        }
    }
}

/** Pure decisions for [DebugReceiver]. */
object DebugTriggers {
    const val TRIP = "dev.tvlauncher.debug.TRIP_SAFE_MODE"
    const val RESET = "dev.tvlauncher.debug.RESET_SAFE_MODE"
    const val CRASH_HOME = "dev.tvlauncher.debug.CRASH_HOME"
    const val OPEN_SETTINGS = "dev.tvlauncher.debug.OPEN_SETTINGS"
    const val EXTRA_COUNT = "count"
    const val EXTRA_SECTION = "section"
    private val SECTION_RE = Regex("[a-z][a-z0-9_]{0,39}")

    sealed class Command {
        data class Trip(val count: Int) : Command()
        object Reset : Command()
        object CrashHome : Command()
        data class OpenSettings(val section: String) : Command()
    }

    /** Only a real Int is accepted; anything else (missing, string, long) means "not given". */
    fun parseCount(raw: Any?): Int? = raw as? Int

    fun knows(action: String?) = action in setOf(TRIP, RESET, CRASH_HOME, OPEN_SETTINGS)

    /** A section id is a plain lower-case identifier (e.g. "home_layout"); anything else is ignored. */
    fun parseSection(raw: Any?): String? = (raw as? String)?.takeIf { SECTION_RE.matches(it) }

    fun decide(action: String?, debuggable: Boolean, count: Int?, section: String? = null): Command? {
        if (!debuggable) return null
        return when (action) {
            TRIP -> Command.Trip((count ?: 3).coerceIn(1, 10))
            RESET -> Command.Reset
            CRASH_HOME -> Command.CrashHome
            OPEN_SETTINGS -> section?.let { Command.OpenSettings(it) }
            else -> null
        }
    }
}
