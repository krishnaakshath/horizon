package dev.tvlauncher.routines

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.tvlauncher.system.PowerActions
import dev.tvlauncher.system.SleepTimer
import dev.tvlauncher.system.SystemPrefs
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.volume.SystemVolume

/** What the end of the sleep timer does (pure, JVM-tested). */
object SleepEndPlan {
    enum class Step { AMBIENT, POWER_MENU, MUTE }

    /** Always the dimmed ambient screen first; then the TV's power menu through the Helper, else mute. */
    fun steps(helperEnabled: Boolean): List<Step> =
        listOf(Step.AMBIENT, if (helperEnabled) Step.POWER_MENU else Step.MUTE)

    const val POWER_MENU_DELAY_MS = 3_000L
}

/** Spotlight's Sleep timer action and the timer's end (reuses system.PowerActions). */
object SleepActions {
    /** One OK press: the next choice (Off, 15, 30, 60, 90, 120 min). Returns the toast line. */
    fun cycle(context: Context): String {
        val left = PowerActions.sleepMinutesLeft(context)
        val current = if (left > 0) SystemPrefs(context).sleepMinutes else 0
        val next = SleepTimer.next(current)
        PowerActions.setSleepTimer(context, next)
        return if (next == 0) "Sleep timer off" else "Sleep in ${SleepTimer.label(next)}"
    }

    /** The timer ran out. */
    fun end(context: Context) {
        val app = context.applicationContext
        val helper = PowerActions.helperEnabled(app)
        for (s in SleepEndPlan.steps(helper)) when (s) {
            SleepEndPlan.Step.AMBIENT -> SystemActions.ambient(app)
            SleepEndPlan.Step.POWER_MENU -> Handler(Looper.getMainLooper()).postDelayed({
                if (!PowerActions.powerMenu(app)) SystemVolume(app).setMuted(true)
            }, SleepEndPlan.POWER_MENU_DELAY_MS)
            SleepEndPlan.Step.MUTE -> SystemVolume(app).setMuted(true)
        }
    }
}
