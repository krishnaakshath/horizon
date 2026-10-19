package dev.tvlauncher.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import dev.tvlauncher.ui.SystemActions

/** Small launcher-owned values for the system pages (one SharedPreferences file, "system_prefs"). */
class SystemPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var sleepEndsAt: Long
        get() = sp.getLong("sleepEndsAt", 0L)
        set(v) { sp.edit().putLong("sleepEndsAt", v).apply() }
    var sleepMinutes: Int
        get() = sp.getInt("sleepMinutes", 0)
        set(v) { sp.edit().putInt("sleepMinutes", v).apply() }
    var textSize: String?
        get() = sp.getString("textSize", null)
        set(v) { sp.edit().putString("textSize", v).apply() }
    /** Room choice to restore when high contrast is switched off. */
    var roomBeforeContrast: String?
        get() = sp.getString("roomBeforeContrast", null)
        set(v) { sp.edit().putString("roomBeforeContrast", v).apply() }
    /** Animation level to restore when Reduce motion is switched off. */
    var motionBefore: String?
        get() = sp.getString("motionBefore", null)
        set(v) { sp.edit().putString("motionBefore", v).apply() }
    var pinHash: String?
        get() = sp.getString("pinHash", null)
        set(v) { sp.edit().putString("pinHash", v).apply() }
    var pinSalt: String
        get() = sp.getString("pinSalt", null) ?: java.util.UUID.randomUUID().toString().also { sp.edit().putString("pinSalt", it).apply() }
        set(v) { sp.edit().putString("pinSalt", v).apply() }

    companion object { const val FILE = "system_prefs" }
}

/**
 * Power: apps cannot put a TV to sleep, so "Turn off" asks the Helper add-on (an accessibility
 * service) for the TV's own power menu. Without it, the remote's power button is the way.
 */
object PowerActions {
    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
    const val ACTION_POWER_MENU = "dev.tvlauncher.helper.action.POWER_MENU"

    fun helperInstalled(context: Context) = safe(false) { context.packageManager.getPackageInfo(HELPER_PACKAGE, 0); true }

    fun helperEnabled(context: Context): Boolean = safe(false) {
        A11yModel.isEnabled(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), A11yModel.HELPER_COMPONENT)
    }

    /** True when the power menu was asked for (the helper is installed and switched on). */
    fun powerMenu(context: Context): Boolean {
        if (!helperEnabled(context)) return false
        return safe(false) { context.sendBroadcast(Intent(ACTION_POWER_MENU).setPackage(HELPER_PACKAGE)); true }
    }

    /** Schedules (or with 0 cancels) the sleep timer. */
    fun setSleepTimer(context: Context, minutes: Int) {
        val prefs = SystemPrefs(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pi = PendingIntent.getBroadcast(context, 0, Intent(context, SleepTimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        safe(Unit) { am?.cancel(pi) }
        prefs.sleepMinutes = minutes
        if (minutes <= 0) { prefs.sleepEndsAt = 0L; return }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        prefs.sleepEndsAt = endsAt
        safe(Unit) { am?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + minutes * 60_000L, pi) }
    }

    fun sleepMinutesLeft(context: Context): Int = SleepTimer.minutesLeft(SystemPrefs(context).sleepEndsAt, System.currentTimeMillis())
}

/** The sleep timer ran out: the TV's power menu through the helper, else the dimmed ambient screen. */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = SystemPrefs(context)
        prefs.sleepEndsAt = 0L
        prefs.sleepMinutes = 0
        if (!PowerActions.powerMenu(context)) SystemActions.ambient(context)
    }
}
