package dev.tvlauncher.insights

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

object UsageAccess {
    /** Exact line to show/run when the TV has no usage-access settings screen. */
    const val ADB_LINE = "adb shell appops set dev.tvlauncher GET_USAGE_STATS allow"

    fun hasAccess(context: Context): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }

    /** Settings screen intent, or null when this device does not resolve it. */
    fun settingsIntent(context: Context): Intent? {
        val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (i.resolveActivity(context.packageManager) != null) i else null
        } catch (_: Throwable) {
            null
        }
    }

    /** Never throws; returns false when there is nothing to open. */
    fun openSettings(context: Context): Boolean = try {
        settingsIntent(context)?.let { context.startActivity(it); true } ?: false
    } catch (_: Throwable) {
        false
    }
}

fun interface UsageSource {
    fun events(fromMs: Long, toMs: Long): List<UsageEvent>
}

/** Packages that are chrome around the content, not viewing. */
val DEFAULT_IGNORE: Set<String> = setOf(
    "dev.tvlauncher", "com.android.systemui", "com.google.android.tvlauncher", "com.tcl.tv"
)

class UsageStatsSource(private val context: Context) : UsageSource {
    override fun events(fromMs: Long, toMs: Long): List<UsageEvent> {
        val out = ArrayList<UsageEvent>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return out
            val resumed = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
            val paused = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_PAUSED else UsageEvents.Event.MOVE_TO_BACKGROUND
            val q = usm.queryEvents(fromMs, toMs) ?: return out
            val e = UsageEvents.Event()
            while (q.hasNextEvent()) {
                q.getNextEvent(e)
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    resumed -> out += UsageEvent(pkg, UsageEvent.Type.RESUMED, e.timeStamp)
                    paused -> out += UsageEvent(pkg, UsageEvent.Type.PAUSED, e.timeStamp)
                }
            }
        } catch (_: Throwable) {
            // SecurityException without access, or OEM quirks: report no data.
        }
        return out
    }
}
