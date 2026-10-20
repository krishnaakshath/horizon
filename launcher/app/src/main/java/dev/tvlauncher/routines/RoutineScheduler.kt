package dev.tvlauncher.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.TimeZone

/** The time routines and their last results, in the shared (household) "routines" file. */
class RoutineStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    data class LastRun(val at: Long, val outcome: RoutinePlan.Outcome)

    fun load(): List<Routine> = try { RoutineModel.fromJson(sp.getString(KEY, null)) } catch (e: Exception) { emptyList() }

    fun save(list: List<Routine>) {
        try { sp.edit().putString(KEY, RoutineModel.toJson(list.take(RoutineModel.MAX))).apply() } catch (e: Exception) { }
    }

    fun upsert(r: Routine) {
        val list = load().toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else if (list.size < RoutineModel.MAX) list += r
        save(list)
    }

    fun delete(id: Int) {
        save(load().filterNot { it.id == id })
        try { sp.edit().remove(lastKey(id)).apply() } catch (e: Exception) { }
    }

    fun lastRun(id: Int): LastRun? = try {
        decodeLast(sp.getString(lastKey(id), null))
    } catch (e: Exception) {
        null
    }

    fun saveLastRun(id: Int, l: LastRun) {
        try { sp.edit().putString(lastKey(id), encodeLast(l)).apply() } catch (e: Exception) { }
    }

    companion object {
        const val FILE = "routines"
        const val KEY = "timeRoutines"
        private fun lastKey(id: Int) = "last_$id"

        fun encodeLast(l: LastRun) = "${l.at}|${l.outcome.name}"

        fun decodeLast(s: String?): LastRun? {
            val parts = s?.split('|') ?: return null
            if (parts.size != 2) return null
            val at = parts[0].toLongOrNull() ?: return null
            val o = RoutinePlan.Outcome.values().firstOrNull { it.name == parts[1] } ?: return null
            return LastRun(at, o)
        }
    }
}

/**
 * One alarm for the soonest routine (RoutineModel.soonest). Exact and allowed while idle where the
 * TV lets us (setExactAndAllowWhileIdle; Android 12+ only with exact-alarm access), else an inexact
 * idle-allowed alarm. Re-armed after each run, every edit, boot, a clock change and a time-zone change.
 */
object RoutineScheduler {
    const val ACTION_FIRE = "dev.tvlauncher.action.ROUTINE_FIRE"
    const val EXTRA_AT = "at"
    const val EXTRA_IDS = "ids"

    /** Exact alarms are allowed below Android 12, and on 12+ only with the special access. */
    fun exactAllowed(sdk: Int, canScheduleExact: Boolean): Boolean = sdk < 31 || canScheduleExact

    fun arm(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = RoutineModel.soonest(RoutineStore(app).load(), now, TimeZone.getDefault())
        val base = Intent(app, RoutineAlarmReceiver::class.java).setAction(ACTION_FIRE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        if (next == null) {
            try { am.cancel(PendingIntent.getBroadcast(app, 0, base, flags)) } catch (e: Exception) { }
            return
        }
        val (at, ids) = next
        val pi = PendingIntent.getBroadcast(app, 0, base.putExtra(EXTRA_AT, at).putExtra(EXTRA_IDS, ids.toIntArray()), flags)
        try {
            val exact = exactAllowed(Build.VERSION.SDK_INT, Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            when {
                Build.VERSION.SDK_INT < 23 -> am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
                exact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (e2: Exception) { }
        } catch (e: Exception) {
        }
    }
}

/** The routine alarm (own PendingIntent only): runs the routines due, then arms the next one. */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoutineScheduler.ACTION_FIRE) return
        val app = context.applicationContext
        val at = intent.getLongExtra(RoutineScheduler.EXTRA_AT, System.currentTimeMillis())
        val ids = intent.getIntArrayExtra(RoutineScheduler.EXTRA_IDS)?.toSet().orEmpty()
        val late = (System.currentTimeMillis() - at).coerceAtLeast(0L)
        try {
            for (r in RoutineStore(app).load()) if (r.id in ids && r.enabled) RoutineRunner.run(app, r, late)
        } finally {
            // Arm from just after this slot, so a routine that fired early by a few ms is not repeated.
            RoutineScheduler.arm(app, maxOf(System.currentTimeMillis(), at + 1))
        }
    }
}

/** Re-arms the routine alarm after boot, an update, a clock change or a time-zone change. */
class RoutineRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> RoutineScheduler.arm(context)
        }
    }
}
