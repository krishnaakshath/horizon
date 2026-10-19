package dev.tvlauncher.power

import android.app.Activity
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import dev.tvlauncher.HomeActivity
import dev.tvlauncher.safemode.SafeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Open home after power on". On a TCL TV, the remote's Power key is suspend-to-RAM: waking just
 * resumes whatever was in front before standby (typically live TV / the last HDMI input), and a
 * cold boot may restore the last source. Signals we act on:
 *  - BOOT_COMPLETED (manifest, [PowerOnReceiver]);
 *  - TCL's "tcl.sys.intent.action.STR_RESUME_COMPLETED" (manifest; sent on every wake with
 *    FLAG_RECEIVER_INCLUDE_BACKGROUND, so it reaches a stopped-in-background launcher);
 *  - ACTION_SCREEN_ON (runtime, while the launcher process lives; the generic Android path).
 * [PowerOnPolicy] decides. The way forward is the Remote helper's accessibility HOME action when
 * that service is on (the system then handles it like the remote's Home key), else a direct start
 * of our home activity. Android 10/11 refuse that from the background unless the launcher holds
 * "Display over other apps" (Android 12+ exempt the default home app). Measured on a TCL 32S5403AF
 * (Android 11): the direct start alone is aborted, and TCL's system UI ignores full-screen
 * notification intents, so those are no fallback. Every step swallows Throwable.
 */
object PowerOn {
    const val TAG = "PowerOn"
    const val ACTION_STR_RESUME = "tcl.sys.intent.action.STR_RESUME_COMPLETED"
    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
    const val HELPER_SERVICE = "dev.tvlauncher.helper.HelperService"
    const val HELPER_RECEIVER = "dev.tvlauncher.helper.PowerOnHomeReceiver"
    const val ACTION_HELPER_HOME = "dev.tvlauncher.helper.action.HOME_AFTER_POWER_ON"
    const val PERMISSION_POWER_HOME = "dev.tvlauncher.permission.POWER_ON_HOME"

    /** Let the top activity settle (TCL restores its source a moment after the wake signal). */
    const val WAKE_DELAY_MS = 1_500L
    const val BOOT_DELAY_MS = 6_000L


    private val main by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var homeResumed = 0
    @Volatile private var installed = false

    /** Application.onCreate: our own view of whether HomeActivity is in front, and SCREEN_ON while alive. */
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(HomeTracker)
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) onSignal(context, PowerOnTrigger.WAKE, "screen on")
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    /** Arms the policy now and decides after a short settle delay. [done] runs after the decision. */
    fun onSignal(context: Context, trigger: PowerOnTrigger, source: String, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        try {
            synchronized(this) {
                val prefs = PowerOnPrefs(app)
                prefs.save(PowerOnPolicy.arm(prefs.state(), trigger, SystemClock.elapsedRealtime()))
            }
            Log.i(TAG, "signal $trigger ($source)")
        } catch (e: Throwable) {
            done?.invoke()
            return
        }
        val delay = if (trigger == PowerOnTrigger.BOOT) BOOT_DELAY_MS else WAKE_DELAY_MS
        main.postDelayed({
            try { decideNow(app, source) } catch (e: Throwable) { Log.w(TAG, "decide failed", e) }
            done?.invoke()
        }, delay)
    }

    private fun decideNow(app: Context, source: String) {
        val prefs = PowerOnPrefs(app)
        val snapshot = snapshot(app, prefs)
        val (decision, next) = synchronized(this) {
            PowerOnPolicy.decide(prefs.state(), snapshot, SystemClock.elapsedRealtime()).also { prefs.save(it.second) }
        }
        Log.i(TAG, "decision $decision ($source) snapshot=$snapshot")
        if (decision != PowerOnDecision.GoHome) {
            val r = (decision as PowerOnDecision.Skip).reason
            if (r == PowerOnDecision.Reason.USER_NAVIGATED || r == PowerOnDecision.Reason.SAFE_MODE || r == PowerOnDecision.Reason.LOOP_GUARD) {
                prefs.lastOutcome = "Left as is (${r.name.lowercase(Locale.ROOT).replace('_', ' ')}) at ${clock()}"
            }
            return
        }
        val steps = PowerOnLadder.steps(
            overlayAllowed = overlayAllowed(app), helperReady = helperReady(app), sdk = Build.VERSION.SDK_INT
        )
        runLadder(app, prefs, steps, 0)
    }

    /**
     * Tries [steps] in order; each one is checked [PowerOnLadder.VERIFY_MS] later by whether our
     * HomeActivity resumed (a refused background start and an ignored HOME action both fail
     * silently). The ladder is finite and runs once per wake, inside the policy's loop guard.
     */
    private fun runLadder(app: Context, prefs: PowerOnPrefs, steps: List<PowerOnLadder.Step>, i: Int) {
        if (homeResumed > 0) {
            val by = steps.getOrNull(i - 1)
            Log.i(TAG, "home in front via ${by ?: "system"}")
            prefs.lastOutcome = "Opened home (${PowerOnLadder.label(by)}) at ${clock()}"
            return
        }
        val step = steps.getOrNull(i)
        if (step == null) {
            Log.w(TAG, "home did not come forward; tried $steps (overlay=${overlayAllowed(app)}, helper=${helperReady(app)})")
            prefs.lastOutcome = "Home could not open at ${clock()}"
            return
        }
        val sent = try {
            when (step) {
                PowerOnLadder.Step.DIRECT -> startHome(app)
                PowerOnLadder.Step.HELPER -> askHelper(app)
            }
        } catch (e: Throwable) { false }
        Log.i(TAG, "try $step sent=$sent")
        main.postDelayed({
            try { runLadder(app, prefs, steps, i + 1) } catch (e: Throwable) { }
        }, if (sent) PowerOnLadder.VERIFY_MS else 0L)
    }

    /** "Display over other apps" granted: Android 10/11 then let the launcher start home from the background. */
    fun overlayAllowed(context: Context): Boolean = try {
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
    } catch (e: Throwable) {
        false
    }

    /** The TV's own "Display over other apps" page for this launcher. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", android.net.Uri.parse("package:${context.packageName}"))

    /** Which way forward this TV has right now (for Settings). */
    fun readiness(context: Context): PowerOnStatus.Readiness = PowerOnStatus.readiness(
        helperReady = helperReady(context),
        overlayAllowed = overlayAllowed(context),
        sdk = Build.VERSION.SDK_INT
    )

    fun snapshot(app: Context, prefs: PowerOnPrefs): PowerOnSnapshot {
        val timeline = try { readTimeline(app) } catch (e: Throwable) { WakeTimeline.Result(null, null, null) }
        val ownFront = homeResumed > 0 || timeline.foreground == app.packageName
        return PowerOnSnapshot(
            enabled = prefs.enabled,
            isHomeApp = isHomeApp(app),
            safeModeClear = safeModeClear(app),
            homeInFront = ownFront,
            foreground = timeline.foreground,
            navigatedAfterWake = timeline.navigatedAfterWake
        )
    }

    fun isHomeApp(context: Context): Boolean = try {
        val ri = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )
        ri?.activityInfo?.packageName == context.packageName
    } catch (e: Throwable) {
        false
    }

    /** Read-only: never writes the crash ledger, never redirects. */
    private fun safeModeClear(context: Context): Boolean = try {
        val h = SafeMode.guard(context).history
        !h.tripped && !h.escapedToSettings
    } catch (e: Throwable) {
        false
    }

    /** The Remote helper is installed and its accessibility service is switched on. */
    fun helperReady(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(HELPER_PACKAGE, 0)
        val on = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        on && list.split(':').any { it.startsWith("$HELPER_PACKAGE/") }
    } catch (e: Throwable) {
        false
    }

    private fun askHelper(context: Context): Boolean = try {
        context.sendBroadcast(
            Intent(ACTION_HELPER_HOME).setClassName(HELPER_PACKAGE, HELPER_RECEIVER)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        ) // no receiverPermission: the helper's receiver declares the signature permission senders need
        true
    } catch (e: Throwable) {
        false
    }

    private fun startHome(context: Context): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClass(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Throwable) {
        false
    }

    /** Usage events of the last minute; empty (all unknown) without usage access. */
    private fun readTimeline(context: Context): WakeTimeline.Result {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return WakeTimeline.Result(null, null, null)
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10 * 60_000L, end) ?: return WakeTimeline.Result(null, null, null)
        val out = ArrayList<WakeTimeline.Event>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when {
                e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND -> WakeTimeline.Kind.RESUMED
                Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE -> WakeTimeline.Kind.SCREEN_ON
                Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE -> WakeTimeline.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            out.add(WakeTimeline.Event(e.timeStamp, kind, e.packageName))
        }
        return WakeTimeline.analyze(out, context.packageName)
    }

    private fun clock(): String = try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    } catch (e: Throwable) { "" }

    private object HomeTracker : Application.ActivityLifecycleCallbacks {
        private fun home(a: Activity) = a is HomeActivity
        override fun onActivityResumed(activity: Activity) { if (home(activity)) homeResumed++ }
        override fun onActivityPaused(activity: Activity) { if (home(activity) && homeResumed > 0) homeResumed-- }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

/** BOOT_COMPLETED and TCL's wake-from-standby broadcast. */
class PowerOnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> PowerOnTrigger.BOOT
            PowerOn.ACTION_STR_RESUME -> PowerOnTrigger.WAKE
            else -> return
        }
        val pending = try { goAsync() } catch (e: Throwable) { null }
        PowerOn.onSignal(context, trigger, intent.action.orEmpty().substringAfterLast('.')) {
            try { pending?.finish() } catch (e: Throwable) { }
        }
    }
}
