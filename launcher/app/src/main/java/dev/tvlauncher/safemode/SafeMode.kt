package dev.tvlauncher.safemode

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
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
        sp.getBoolean(KEY_SAFE_CRASHED, false)
    )

    override fun write(history: CrashHistory) {
        sp.edit()
            .putString(KEY_TIMES, CrashHistory.encodeTimes(history.times))
            .putString(KEY_LAST, history.lastException)
            .putBoolean(KEY_TRIPPED, history.tripped)
            .putLong(KEY_TRIPPED_AT, history.trippedAt)
            .putInt(KEY_TRIP_BOOT, history.tripBoot)
            .putLong(KEY_RETRIED_AT, history.retriedAt)
            .putBoolean(KEY_SAFE_CRASHED, history.safeModeCrashed)
            .commit()
    }

    private companion object {
        const val KEY_TIMES = "times"
        const val KEY_LAST = "last"
        const val KEY_TRIPPED = "tripped"
        const val KEY_TRIPPED_AT = "tripped_at"
        const val KEY_TRIP_BOOT = "trip_boot"
        const val KEY_RETRIED_AT = "retried_at"
        const val KEY_SAFE_CRASHED = "safe_mode_crashed"
    }
}

/** Android glue for [CrashGuard]: the uncaught-exception hook and HomeActivity's redirect. */
object SafeMode {
    fun guard(context: Context): CrashGuard {
        val resolver = context.applicationContext.contentResolver
        return CrashGuard(PrefsCrashStore(context), { System.currentTimeMillis() }) {
            try { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1) } catch (e: Throwable) { -1 }
        }
    }

    /**
     * Application.onCreate: tracks which activity is in charge ([CrashScope]) and records only
     * home-screen and process-start crashes (or flags a safe-mode crash), then hands every crash
     * to the previous handler.
     */
    fun install(app: Application) {
        val guard = guard(app)
        val scope = CrashScope(HomeActivity::class.java.name, SafeModeActivity::class.java.name)
        app.registerActivityLifecycleCallbacks(ScopeCallbacks(scope))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val name = error.javaClass.simpleName.ifEmpty { "Throwable" }
                when {
                    scope.inSafeMode() -> guard.recordSafeModeCrash(name)
                    scope.shouldRecordCrash() -> guard.recordCrash(name)
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
     * Call first in HomeActivity.onCreate (after super). When the guard has tripped, opens
     * [SafeModeActivity], finishes [home] and returns true; the caller must then return at once.
     */
    fun redirectIfTripped(home: Activity): Boolean = try {
        if (!guard(home).shouldRedirect()) {
            false
        } else {
            home.startActivity(Intent(home, SafeModeActivity::class.java))
            home.overridePendingTransition(0, 0)
            home.finish()
            true
        }
    } catch (e: RuntimeException) {
        false // never block the home screen because of the guard itself
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
