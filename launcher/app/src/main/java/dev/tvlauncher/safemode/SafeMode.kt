package dev.tvlauncher.safemode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Process
import kotlin.system.exitProcess

/** SharedPreferences "crash_guard"; written with commit() because the process is about to die. */
class PrefsCrashStore(context: Context) : CrashStore {
    private val sp = context.applicationContext.getSharedPreferences("crash_guard", Context.MODE_PRIVATE)

    override fun read() = CrashHistory(
        CrashHistory.decodeTimes(sp.getString(KEY_TIMES, null)),
        sp.getString(KEY_LAST, null),
        sp.getBoolean(KEY_TRIPPED, false)
    )

    override fun write(history: CrashHistory) {
        sp.edit()
            .putString(KEY_TIMES, CrashHistory.encodeTimes(history.times))
            .putString(KEY_LAST, history.lastException)
            .putBoolean(KEY_TRIPPED, history.tripped)
            .commit()
    }

    private companion object {
        const val KEY_TIMES = "times"
        const val KEY_LAST = "last"
        const val KEY_TRIPPED = "tripped"
    }
}

/** Android glue for [CrashGuard]: the uncaught-exception hook and HomeActivity's redirect. */
object SafeMode {
    fun guard(context: Context) = CrashGuard(PrefsCrashStore(context)) { System.currentTimeMillis() }

    /** Application.onCreate: records every uncaught crash, then hands it to the previous handler. */
    fun install(context: Context) {
        val guard = guard(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                guard.recordCrash(error.javaClass.simpleName.ifEmpty { "Throwable" })
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
        if (!SafeModeGate.shouldRedirect(guard(home).history)) {
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
