package dev.tvlauncher.link

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.EditText
import dev.tvlauncher.HomeActivity
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.core.LinkContract.Outcome
import dev.tvlauncher.power.PowerOn
import dev.tvlauncher.system.PowerActions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Which of our activities is resumed (null when another app is in front). */
object ForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile var resumed: Activity? = null
        private set
    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) { resumed = activity }
    override fun onActivityPaused(activity: Activity) { if (resumed === activity) resumed = null }
    override fun onActivityDestroyed(activity: Activity) { if (resumed === activity) resumed = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}

/** Pure decisions of the remote, testable without Android. */
object LinkCommands {
    /**
     * Keys and text while another app is in front: only Home is ours to do (see goHome); the
     * Helper has no key or text action (it cannot read other apps' screens, by design), so the
     * phone shows its calm "needs H02 Helper" note.
     */
    fun outsideOutcome(key: LinkContract.Key?): Int = outsideOutcome(key, helperOn = false)

    /** Back in another app goes through the Helper's existing global BACK action when it is on. */
    fun outsideOutcome(key: LinkContract.Key?, helperOn: Boolean): Int = when {
        key == LinkContract.Key.HOME -> Outcome.LAUNCHER
        key == LinkContract.Key.BACK && helperOn -> Outcome.HELPER
        else -> Outcome.NEEDS_HELPER
    }

    /** The phone's quick action names mapped to Spotlight items (Spotlight itself is null: it opens the overlay). */
    fun quickItem(wire: String?): dev.tvlauncher.ui.QuickActions.Item? = when (wire) {
        LinkContract.QUICK_INPUTS -> dev.tvlauncher.ui.QuickActions.Item.INPUTS
        LinkContract.QUICK_SETTINGS -> dev.tvlauncher.ui.QuickActions.Item.SETTINGS
        LinkContract.QUICK_AMBIENT -> dev.tvlauncher.ui.QuickActions.Item.AMBIENT
        LinkContract.QUICK_FOCUS -> dev.tvlauncher.ui.QuickActions.Item.FOCUS
        LinkContract.QUICK_BEDTIME -> dev.tvlauncher.ui.QuickActions.Item.BEDTIME
        else -> null
    }

    fun quickAllowed(wire: String?): Boolean = wire != null && wire in LinkContract.QUICK_ITEMS

    /**
     * Which session the phone controls: the first playing one, else the first with any state
     * (index into [states], or -1). States are PlaybackState codes (3 = playing, 6 = buffering).
     */
    fun pickSession(states: List<Int?>): Int {
        val playing = states.indexOfFirst { it == 3 || it == 6 }
        if (playing >= 0) return playing
        return states.indexOfFirst { it != null && it != 0 }
    }

    /** Packages the phone may not open (hidden TV firmware apps and our own add-ons). */
    fun launchAllowed(pkg: String?): Boolean =
        LinkContract.isValidPackage(pkg) && !pkg!!.startsWith("com.tcl.") && !pkg.startsWith("com.android.") &&
            pkg != LinkContract.LINK_PACKAGE && pkg != LinkContract.HELPER_PACKAGE
}

/**
 * Remote commands from H02 Link (ordered broadcast, sender must hold [LinkContract.PERMISSION]).
 * Our own screens in front: keys and text go to the focused view. Another app in front: Home
 * brings the home screen back (through the Helper's existing HOME action when it is on), links
 * and app launches work everywhere, other keys answer [Outcome.NEEDS_HELPER].
 */
class LinkCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LinkContract.ACTION_COMMAND) return
        val code = try { handle(context.applicationContext, intent) } catch (e: Throwable) { Outcome.FAILED }
        if (isOrderedBroadcast) resultCode = code
    }

    private fun handle(context: Context, intent: Intent): Int {
        val kind = intent.getStringExtra(LinkContract.EXTRA_KIND) ?: return Outcome.FAILED
        return when (kind) {
            LinkContract.KIND_KEY -> key(context, LinkContract.Key.fromWire(intent.getStringExtra(LinkContract.EXTRA_KEY)) ?: return Outcome.FAILED)
            LinkContract.KIND_TEXT, LinkContract.KIND_ENTER -> text(LinkContract.cleanText(intent.getStringExtra(LinkContract.EXTRA_TEXT)), kind == LinkContract.KIND_ENTER)
            LinkContract.KIND_OPEN_URL -> openUrl(context, intent.getStringExtra(LinkContract.EXTRA_URL))
            LinkContract.KIND_LAUNCH -> launch(context, intent.getStringExtra(LinkContract.EXTRA_PACKAGE))
            LinkContract.KIND_MEDIA -> LinkMedia.transport(context, intent.getStringExtra(LinkContract.EXTRA_ACTION))
            LinkContract.KIND_NOW_PLAYING -> { setResultExtras(LinkMedia.nowPlaying(context)); Outcome.LAUNCHER }
            LinkContract.KIND_POWER_MENU -> if (PowerActions.powerMenu(context)) Outcome.HELPER else Outcome.NEEDS_HELPER
            LinkContract.KIND_QUICK -> quick(context, intent.getStringExtra(LinkContract.EXTRA_ITEM))
            else -> Outcome.FAILED
        }
    }

    private fun key(context: Context, key: LinkContract.Key): Int {
        if (key == LinkContract.Key.HOME) return goHome(context)
        if (ForegroundTracker.resumed == null) {
            val helperOn = PowerActions.helperEnabled(context)
            val out = LinkCommands.outsideOutcome(key, helperOn)
            if (out == Outcome.HELPER) return helperGlobal(context, "back")
            return out
        }
        if (key == LinkContract.Key.OK_LONG) return if (onMain { a -> sendLongPress(a, key.keyCode); true }) Outcome.LAUNCHER else Outcome.NEEDS_HELPER
        return if (onMain { a -> sendKey(a, key.keyCode); true }) Outcome.LAUNCHER else Outcome.NEEDS_HELPER
    }

    /** The Helper's existing global actions (BACK only from here; HOME uses its own receiver). */
    private fun helperGlobal(context: Context, key: String): Int = try {
        context.sendBroadcast(Intent(LinkContract.ACTION_HELPER_INPUT).setPackage(LinkContract.HELPER_PACKAGE)
            .putExtra(LinkContract.EXTRA_KEY, key))
        Outcome.HELPER
    } catch (e: RuntimeException) {
        Outcome.NEEDS_HELPER
    }

    private fun quick(context: Context, item: String?): Int {
        if (!LinkCommands.quickAllowed(item)) return Outcome.FAILED
        return try {
            context.startActivity(Intent(context, HomeActivity::class.java)
                .setAction(LinkContract.ACTION_QUICK_HOME)
                .putExtra(LinkContract.EXTRA_ITEM, item)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            Outcome.LAUNCHER
        } catch (e: RuntimeException) {
            Outcome.FAILED
        }
    }

    private fun goHome(context: Context): Int {
        if (ForegroundTracker.resumed == null && PowerActions.helperEnabled(context)) {
            try {
                context.sendBroadcast(Intent(PowerOn.ACTION_HELPER_HOME).setClassName(PowerOn.HELPER_PACKAGE, PowerOn.HELPER_RECEIVER))
                return Outcome.HELPER
            } catch (e: RuntimeException) {
                // Fall through to starting the home screen directly.
            }
        }
        return try {
            context.startActivity(Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            Outcome.LAUNCHER
        } catch (e: RuntimeException) {
            Outcome.NEEDS_HELPER
        }
    }

    private fun text(text: String, enter: Boolean): Int {
        if (ForegroundTracker.resumed == null) return Outcome.NEEDS_HELPER
        val ok = onMain { a ->
            val field = a.currentFocus as? EditText ?: return@onMain false
            if (text.isNotEmpty()) {
                val e = field.text
                val start = maxOf(0, minOf(field.selectionStart, field.selectionEnd))
                val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(start)
                e.replace(start, end.coerceAtMost(e.length), text)
            }
            if (enter) {
                field.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)
            }
            true
        }
        return if (ok) Outcome.LAUNCHER else Outcome.FAILED
    }

    private fun openUrl(context: Context, url: String?): Int {
        if (!LinkContract.isOpenableUrl(url)) return Outcome.FAILED
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(LinkContract.BROWSER_PACKAGE)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.LAUNCHER
        } catch (e: RuntimeException) {
            Outcome.FAILED
        }
    }

    private fun launch(context: Context, pkg: String?): Int {
        if (!LinkCommands.launchAllowed(pkg)) return Outcome.FAILED
        val pm = context.packageManager
        val i = try {
            pm.getLeanbackLaunchIntentForPackage(pkg!!) ?: pm.getLaunchIntentForPackage(pkg)
        } catch (e: RuntimeException) {
            null
        } ?: return Outcome.FAILED
        return try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.LAUNCHER
        } catch (e: RuntimeException) {
            Outcome.FAILED
        }
    }

    private fun sendKey(a: Activity, code: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        a.dispatchKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
    }

    /** Down, held past the long-press timeout (with a long-press repeat), then up. */
    private fun sendLongPress(a: Activity, code: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        val hold = android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150
        Handler(Looper.getMainLooper()).postDelayed({
            if (a.isFinishing) return@postDelayed
            val now = SystemClock.uptimeMillis()
            a.dispatchKeyEvent(KeyEvent(t, now, KeyEvent.ACTION_DOWN, code, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS))
            a.dispatchKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
        }, hold)
    }

    /** Runs [f] on the main thread with the resumed activity; waits briefly for the answer. */
    private fun onMain(f: (Activity) -> Boolean): Boolean {
        val result = AtomicBoolean(false)
        val run = Runnable {
            val a = ForegroundTracker.resumed
            if (a != null && !a.isFinishing) result.set(try { f(a) } catch (e: Throwable) { false })
        }
        if (Looper.myLooper() == Looper.getMainLooper()) { run.run(); return result.get() }
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { run.run(); done.countDown() }
        done.await(800, TimeUnit.MILLISECONDS)
        return result.get()
    }
}
