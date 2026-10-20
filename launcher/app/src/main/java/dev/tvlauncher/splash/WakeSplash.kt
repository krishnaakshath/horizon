package dev.tvlauncher.splash

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * "Also after standby": the power-on code (or the SCREEN_ON receiver installed by [install]) calls
 * [onWake]; the next time the home screen starts it [consume]s the flag and plays the start-up
 * animation over the home. The flag expires after [WINDOW_MS] so a wake long ago never replays.
 */
object WakeSplash {
    const val WINDOW_MS = 15_000L

    @Volatile private var wokeAt = 0L

    /** Pure: whether a wake at [wokeAt] should still play at [now] for [settings]. */
    fun shouldPlay(settings: MotionStyleSettings, wokeAt: Long, now: Long): Boolean =
        settings.startupWhen == StartupWhen.ALSO_WAKE && settings.startup != StartupStyle.OFF &&
            wokeAt > 0L && now - wokeAt in 0..WINDOW_MS

    /** The hook: the TV woke from standby. Safe to call from any thread, any number of times. */
    fun onWake(context: Context, now: Long = android.os.SystemClock.uptimeMillis()) {
        if (MotionStylePrefs(context).load().startupWhen == StartupWhen.ALSO_WAKE) wokeAt = now
    }

    /** True once per wake when the start-up animation should play now. */
    fun consume(context: Context, now: Long = android.os.SystemClock.uptimeMillis()): Boolean {
        val at = wokeAt
        wokeAt = 0L
        return shouldPlay(MotionStylePrefs(context).load(), at, now)
    }

    /** Application.onCreate: listens for the screen coming on while the launcher process lives. */
    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) onWake(context)
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }
}
