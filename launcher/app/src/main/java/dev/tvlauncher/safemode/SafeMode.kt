package dev.tvlauncher.safemode

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import dev.tvlauncher.HomeActivity
import kotlin.system.exitProcess

/** SharedPreferences "crash_guard"; written with commit() because the process is about to die. */
class PrefsCrashStore(context: Context) : CrashStore {
    private val sp = context.applicationContext.getSharedPreferences("crash_guard", Context.MODE_PRIVATE)

    override fun read() = CrashHistory(
        CrashHistory.decodeTimes(sp.getString(KEY_TIMES, null)),
        sp.getString(KEY_LAST, null),
        sp.getBoolean(KEY_TRIPPED, false),
        sp.getLong(KEY_TRIPPED_AT, 0L),
        sp.getInt(KEY_TRIP_BOOT, -1),
        sp.getLong(KEY_RETRIED_AT, 0L),
        CrashHistory.decodeTimes(sp.getString(KEY_SAFE_TIMES, null)),
        sp.getBoolean(KEY_ESCAPED, false),
        sp.getLong(KEY_ESCAPED_AT, 0L),
        sp.getInt(KEY_ESCAPE_BOOT, -1),
        sp.getLong(KEY_TRIP_ELAPSED, -1L),
        sp.getLong(KEY_ESCAPE_ELAPSED, -1L)
    )

    override fun write(history: CrashHistory) {
        sp.edit()
            .putString(KEY_TIMES, CrashHistory.encodeTimes(history.times))
            .putString(KEY_LAST, history.lastException)
            .putBoolean(KEY_TRIPPED, history.tripped)
            .putLong(KEY_TRIPPED_AT, history.trippedAt)
            .putInt(KEY_TRIP_BOOT, history.tripBoot)
            .putLong(KEY_RETRIED_AT, history.retriedAt)
            .putString(KEY_SAFE_TIMES, CrashHistory.encodeTimes(history.safeModeCrashTimes))
            .putBoolean(KEY_ESCAPED, history.escapedToSettings)
            .putLong(KEY_ESCAPED_AT, history.escapedAt)
            .putInt(KEY_ESCAPE_BOOT, history.escapeBoot)
            .putLong(KEY_TRIP_ELAPSED, history.tripElapsed)
            .putLong(KEY_ESCAPE_ELAPSED, history.escapeElapsed)
            .remove("safe_mode_crashed") // older format
            .commit()
    }

    private companion object {
        const val KEY_TIMES = "times"
        const val KEY_LAST = "last"
        const val KEY_TRIPPED = "tripped"
        const val KEY_TRIPPED_AT = "tripped_at"
        const val KEY_TRIP_BOOT = "trip_boot"
        const val KEY_RETRIED_AT = "retried_at"
        const val KEY_SAFE_TIMES = "safe_mode_crashes"
        const val KEY_ESCAPED = "escaped_to_settings"
        const val KEY_ESCAPED_AT = "escaped_at"
        const val KEY_ESCAPE_BOOT = "escape_boot"
        const val KEY_TRIP_ELAPSED = "trip_elapsed"
        const val KEY_ESCAPE_ELAPSED = "escape_elapsed"
    }
}

/** Android glue for [CrashGuard]: the uncaught-exception hook and HomeActivity's redirect. */
object SafeMode {
    fun guard(context: Context): CrashGuard {
        val resolver = context.applicationContext.contentResolver
        return CrashGuard(
            PrefsCrashStore(context),
            { System.currentTimeMillis() },
            { try { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1) } catch (e: Throwable) { -1 } },
            { SystemClock.elapsedRealtime() }
        )
    }

    /** The process's [CrashScope] once [install] ran; the redirect announces safe mode to it. */
    @Volatile
    private var scope: CrashScope? = null

    /**
     * Application.onCreate: tracks which activity is in charge ([CrashScope]) and records only
     * home-screen and process-start crashes (or flags a safe-mode crash), then hands every crash
     * to the previous handler.
     */
    fun install(app: Application) {
        val guard = guard(app)
        val scope = CrashScope(HomeActivity::class.java.name, SafeModeActivity::class.java.name)
        this.scope = scope
        app.registerActivityLifecycleCallbacks(ScopeCallbacks(scope))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val name = error.javaClass.simpleName.ifEmpty { "Throwable" }
                when (CrashAttribution.classify(scope, guard.isTripped)) {
                    CrashAttribution.Kind.SAFE_MODE -> guard.recordSafeModeCrash(name)
                    CrashAttribution.Kind.HOME -> guard.recordCrash(name)
                    CrashAttribution.Kind.IGNORE -> Unit
                }
            } catch (e: Throwable) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /**
     * Call first in HomeActivity.onCreate (after super). Walks the [EscapePlan]: full or minimal
     * safe mode, or straight to the TV's Settings app. When it redirects it finishes [home] and
     * returns true; the caller must then return at once. Never throws (fails open to home).
     */
    fun redirectIfTripped(home: Activity): Boolean = try {
        when (guard(home).decide()) {
            EscapePlan.Level.HOME -> false
            EscapePlan.Level.SAFE_MODE_FULL -> leave(home) { startSafeMode(home, minimal = false) }
            EscapePlan.Level.SAFE_MODE_MINIMAL -> leave(home) { startSafeMode(home, minimal = true) }
            // Terminal level: a usable stock launcher, else the framework Settings app; safe mode last.
            EscapePlan.Level.SYSTEM_SETTINGS -> leave(home) { startTerminal(home) }
        }
    } catch (e: Throwable) {
        false
    }

    private inline fun leave(home: Activity, start: () -> Boolean): Boolean {
        if (!start()) return false
        try { home.overridePendingTransition(0, 0) } catch (e: Throwable) { }
        home.finish()
        return true
    }

    private fun startTerminal(home: Activity): Boolean {
        for (t in EscapePlan.terminalTargets(StockLauncher.intent(home) != null)) {
            val ok = when (t) {
                EscapePlan.Terminal.STOCK_LAUNCHER -> StockLauncher.start(home)
                EscapePlan.Terminal.SYSTEM_SETTINGS -> SettingsEscape.open(home)
                EscapePlan.Terminal.SAFE_MODE_MINIMAL -> startSafeMode(home, minimal = true)
            }
            if (ok) return true
        }
        return false
    }

    private fun startSafeMode(home: Activity, minimal: Boolean): Boolean = try {
        // Anything that fails from here until safe mode reports itself is safe mode's crash.
        scope?.onCreated(SafeModeActivity::class.java.name)
        home.startActivity(
            Intent(home, SafeModeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SafeModeActivity.EXTRA_MINIMAL, minimal)
        )
        true
    } catch (e: Throwable) {
        scope?.onCreated(HomeActivity::class.java.name) // not started: home keeps running
        false
    }
}

private class ScopeCallbacks(private val scope: CrashScope) : Application.ActivityLifecycleCallbacks {
    private fun n(a: Activity) = a.javaClass.name
    // API 29+: before the activity's own onCreate body, so even theme/inflate crashes are attributed.
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) = scope.onCreated(n(activity))
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = scope.onCreated(n(activity))
    override fun onActivityStarted(activity: Activity) = scope.onStarted(n(activity))
    override fun onActivityResumed(activity: Activity) = scope.onResumed(n(activity))
    override fun onActivityPaused(activity: Activity) = scope.onPaused(n(activity))
    override fun onActivityStopped(activity: Activity) = scope.onStopped(n(activity))
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = scope.onDestroyed(n(activity))
}
