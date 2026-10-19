package dev.tvlauncher.speed

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import dev.tvlauncher.HomeActivity
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.dns.PrivateDnsSection
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Wiring for Settings > System > Speed: the nightly cleanup (an inexact quiet-hour alarm, an
 * idle job and screen-off, all gated by [AutoPolicy] and [RateLimit], so they never add up to
 * more than one run), and "Free memory when you go Home". [install] runs once from
 * LauncherApp.onCreate; every entry point swallows Throwable.
 */
object Speed {
    private const val ALARM_REQUEST = 4101
    const val JOB_ID = 4102
    private const val SCREEN_OFF_DELAY_MS = 20_000L
    const val ACTION_ALARM = "dev.tvlauncher.speed.action.NIGHTLY"

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "speed").apply { isDaemon = true }
    }

    @Volatile private var installed = false
    @Volatile private var homeResumed = false
    private var pendingHome: Runnable? = null
    private var pendingScreenOff: Runnable? = null

    val isHomeInFront: Boolean get() = homeResumed

    fun install(app: Application) {
        if (installed) return
        installed = true
        try { registerSections() } catch (e: Throwable) { }
        try { app.registerActivityLifecycleCallbacks(HomeWatcher(app)) } catch (e: Throwable) { }
        try {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onScreen(app, intent.action == Intent.ACTION_SCREEN_OFF)
            }, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
        } catch (e: Throwable) { }
        try { schedule(app) } catch (e: Throwable) { }
    }

    /** Settings > System > Speed and Settings > Connections > Private DNS. */
    fun registerSections(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(SpeedSection)
        registry.register(PrivateDnsSection)
    }

    /** (Re)arms the quiet-hour alarm and the idle job, or cancels both when automatic cleanup is off. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val prefs = SpeedPrefs(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pi = alarmIntent(app)
        val js = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        if (!prefs.autoEnabled) {
            try { am?.cancel(pi) } catch (e: Throwable) { }
            try { js?.cancel(JOB_ID) } catch (e: Throwable) { }
            return
        }
        try {
            val at = SpeedSchedule.nextQuietTime(System.currentTimeMillis(), prefs.quietHour, TimeZone.getDefault())
            // Inexact on purpose: the system batches it with other wakeups.
            am?.setWindow(AlarmManager.RTC_WAKEUP, at, SpeedSchedule.WINDOW_MS, pi)
        } catch (e: Throwable) {
            Log.w(SpeedCleaner.TAG, "alarm not set", e)
        }
        try {
            if (js != null && js.allPendingJobs.none { it.id == JOB_ID }) {
                js.schedule(
                    JobInfo.Builder(JOB_ID, ComponentName(app, SpeedJobService::class.java))
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(12 * 60 * 60_000L)
                        .setPersisted(true)
                        .build()
                )
            }
        } catch (e: Throwable) {
            Log.w(SpeedCleaner.TAG, "job not set", e)
        }
    }

    private fun alarmIntent(context: Context): PendingIntent {
        val i = Intent(context, SpeedAlarmReceiver::class.java).setAction(ACTION_ALARM)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, ALARM_REQUEST, i, flags)
    }

    /** One unattended attempt; [done] runs on the worker thread afterwards. */
    fun tryAuto(context: Context, source: String, done: () -> Unit = {}) {
        val app = context.applicationContext
        worker.execute {
            try {
                val prefs = SpeedPrefs(app)
                val now = System.currentTimeMillis()
                val d = AutoPolicy.decide(
                    enabled = prefs.autoEnabled,
                    now = now,
                    lastAutoRun = prefs.lastAutoRun,
                    everyDays = prefs.everyDays,
                    screenOn = SpeedSystem.screenOn(app),
                    playing = SpeedSystem.media(app).anyPlaying,
                    rateOk = RateLimit.allows(Trigger.AUTO, now, prefs.history(), prefs.unattended()),
                    running = SpeedCleaner.isRunning
                )
                Log.i(SpeedCleaner.TAG, "auto ($source): $d")
                if (d == AutoDecision.Run) {
                    // The screen may come back on mid-run: stop closing apps then.
                    SpeedCleaner.run(app, Trigger.AUTO, homeInFront = homeResumed, proceed = { !SpeedSystem.screenOn(app) })
                }
            } catch (e: Throwable) {
                Log.w(SpeedCleaner.TAG, "auto failed", e)
            } finally {
                try { done() } catch (e: Throwable) { }
            }
        }
    }

    /** Manual "Clean up now" from Settings, on the worker thread. */
    fun cleanNow(context: Context, progress: (SpeedCleaner.Step) -> Unit, done: (CleanupResult?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val r = try { SpeedCleaner.run(app, Trigger.MANUAL, homeInFront = false, progress = progress) } catch (e: Throwable) { null }
            main.post { done(r) }
        }
    }

    private fun onScreen(app: Context, off: Boolean) {
        pendingScreenOff?.let { main.removeCallbacks(it) }
        pendingScreenOff = null
        if (!off) return
        val r = Runnable { tryAuto(app, "screen off") }
        pendingScreenOff = r
        main.postDelayed(r, SCREEN_OFF_DELAY_MS)
    }

    private fun onHomeResumed(app: Context) {
        homeResumed = true
        pendingHome?.let { main.removeCallbacks(it) }
        val prefs = SpeedPrefs(app)
        if (!prefs.freeOnHome) return
        val r = Runnable {
            if (!homeResumed) return@Runnable
            worker.execute {
                try {
                    val now = System.currentTimeMillis()
                    val p = SpeedPrefs(app)
                    val ok = HomePolicy.shouldRun(
                        enabled = p.freeOnHome,
                        usageAccess = SpeedSystem.usageAccess(app),
                        playing = SpeedSystem.media(app).anyPlaying,
                        rateOk = RateLimit.allows(Trigger.HOME, now, p.history(), p.unattended()),
                        running = SpeedCleaner.isRunning
                    )
                    if (ok && homeResumed) SpeedCleaner.run(app, Trigger.HOME, homeInFront = true, proceed = { homeResumed })
                } catch (e: Throwable) { }
            }
        }
        pendingHome = r
        main.postDelayed(r, HomePolicy.SETTLE_MS)
    }

    private fun onHomePaused() {
        homeResumed = false
        pendingHome?.let { main.removeCallbacks(it) }
        pendingHome = null
    }

    private class HomeWatcher(private val app: Context) : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) {
            if (a is HomeActivity) try { onHomeResumed(app) } catch (e: Throwable) { }
        }

        override fun onActivityPaused(a: Activity) {
            if (a is HomeActivity) onHomePaused()
        }

        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }
}

/** The quiet-hour alarm: one attempt, then the next alarm. */
class SpeedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Speed.ACTION_ALARM) return
        val pending = try { goAsync() } catch (e: Throwable) { null }
        try { Speed.schedule(context) } catch (e: Throwable) { }
        Speed.tryAuto(context, "quiet hour") { try { pending?.finish() } catch (e: Throwable) { } }
    }
}

/** Alarms do not survive a reboot or an update: re-arm them. Nothing else runs here. */
class SpeedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> try { Speed.schedule(context) } catch (e: Throwable) { }
        }
    }
}

/** The system says the TV is idle (screen off for a while): one attempt. */
class SpeedJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Speed.tryAuto(this, "idle job") { try { jobFinished(params, false) } catch (e: Throwable) { } }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
