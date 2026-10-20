package dev.tvlauncher.routines

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import dev.tvlauncher.focus.FocusMode
import dev.tvlauncher.power.PowerOn
import dev.tvlauncher.profiles.ProfileManager
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.ui.SystemActions

/** What a fired routine did (pure, JVM-tested). */
object RoutinePlan {
    enum class Outcome(val label: String) {
        RAN("Ran on time"),
        SKIP_STANDBY("Skipped (TV was off)"),
        SKIP_LATE("Skipped (the TV woke up too late)"),
        SKIP_BLOCKED("Skipped (allow Display over other apps)"),
        SKIP_LOCKED("Skipped (that profile has a PIN)"),
        SKIP_MISSING("Skipped (the app or profile is gone)"),
        FAILED("Could not run")
    }

    /** Facts about the TV when the alarm arrived. */
    data class Facts(
        val lateMs: Long,
        val screenOn: Boolean,
        /** Android 10+ refuses background activity starts unless "Display over other apps" is allowed or we are in front. */
        val mayStartScreens: Boolean,
        val targetExists: Boolean = true,
        val targetLocked: Boolean = false
    )

    /** May an on-screen action start an activity from the background on [sdk]? */
    fun mayStart(sdk: Int, overlayAllowed: Boolean, inFront: Boolean): Boolean = sdk < 29 || overlayAllowed || inFront

    fun decide(action: RoutineAction, f: Facts): Outcome {
        when (RoutineModel.decide(action.kind, f.lateMs, f.screenOn)) {
            RoutineModel.Decision.SKIP_STANDBY -> return Outcome.SKIP_STANDBY
            RoutineModel.Decision.SKIP_LATE -> return Outcome.SKIP_LATE
            RoutineModel.Decision.RUN -> Unit
        }
        if (!f.targetExists) return Outcome.SKIP_MISSING
        if (action is RoutineAction.Profile && f.targetLocked) return Outcome.SKIP_LOCKED
        if (action.kind.needsScreen && !f.mayStartScreens) return Outcome.SKIP_BLOCKED
        return Outcome.RAN
    }

    /** "Last run Mon 07:00 · Ran on time"; "Not run yet" before the first time. */
    fun lastLine(last: RoutineStore.LastRun?, format: (Long) -> String): String =
        if (last == null) "Not run yet" else "Last run ${format(last.at)} · ${last.outcome.label}"
}

/**
 * Runs one routine's action with the launcher's existing pieces: ambient (SystemActions), room
 * lighting (RoomPrefs), profiles (ProfileManager, never into a PIN-locked one), Bedtime, an app
 * (its TV launch intent) and Focus mode. On-screen actions need the screen on and, on Android 10+,
 * "Display over other apps" (the same route the power-on code uses) unless the launcher is in front.
 */
object RoutineRunner {
    fun run(context: Context, r: Routine, lateMs: Long): RoutinePlan.Outcome {
        val app = context.applicationContext
        val outcome = try {
            val facts = facts(app, r.action, lateMs)
            val o = RoutinePlan.decide(r.action, facts)
            if (o == RoutinePlan.Outcome.RAN && !apply(app, r.action)) RoutinePlan.Outcome.FAILED else o
        } catch (e: Throwable) {
            RoutinePlan.Outcome.FAILED
        }
        RoutineStore(app).saveLastRun(r.id, RoutineStore.LastRun(System.currentTimeMillis(), outcome))
        return outcome
    }

    private fun facts(app: Context, a: RoutineAction, lateMs: Long): RoutinePlan.Facts {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val screenOn = pm?.isInteractive ?: true
        val may = RoutinePlan.mayStart(Build.VERSION.SDK_INT, PowerOn.overlayAllowed(app), inFront())
        var exists = true
        var locked = false
        when (a) {
            is RoutineAction.Launch -> exists = launchIntent(app, a.pkg) != null
            is RoutineAction.Profile -> {
                val p = ProfileManager.load(app).byId(a.id)
                exists = p != null
                locked = p?.hasPin == true
            }
            else -> Unit
        }
        return RoutinePlan.Facts(lateMs, screenOn, may, exists, locked)
    }

    private fun inFront(): Boolean = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (e: Throwable) {
        false
    }

    fun launchIntent(app: Context, pkg: String): Intent? = try {
        app.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: app.packageManager.getLaunchIntentForPackage(pkg)
    } catch (e: Throwable) {
        null
    }

    private fun apply(app: Context, a: RoutineAction): Boolean {
        when (a) {
            RoutineAction.Ambient -> return SystemActions.ambient(app)
            RoutineAction.BedtimeOn -> Bedtime.on(app)
            RoutineAction.BedtimeOff -> Bedtime.off(app)
            is RoutineAction.Room -> {
                val c = RoomChoice.parse(a.choice) ?: return false
                val prefs = RoomPrefs(app)
                prefs.save(prefs.load().copy(choice = c))
                RoutineSignals.fire()
            }
            is RoutineAction.Profile -> {
                if (ProfileManager.switchTo(app, a.id) is dev.tvlauncher.profiles.ProfileModel.Result.Error) return false
                RoutineSignals.fire()
            }
            is RoutineAction.Launch -> return SystemActions.start(app, launchIntent(app, a.pkg) ?: return false)
            is RoutineAction.Focus -> if (a.start) FocusMode.start(app) else FocusMode.stop(app)
        }
        return true
    }
}
