package dev.tvlauncher.setup

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.room.RoomSettings
import java.util.concurrent.Executors

/**
 * The small public API of the setup package, for other screens: status checks, the calm
 * replacement lines, and the automatic configuration. Nothing here throws.
 */
object SetupStatus {
    private val worker by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "setup-auto").apply { isDaemon = true } } }

    /** The setup helper granted WRITE_SECURE_SETTINGS, so the launcher can switch things on itself. */
    fun canWriteSecureSettings(context: Context): Boolean = SetupProber.canWriteSecure(context)

    fun notificationAccessHint(settingsOpened: Boolean): String = SetupCopy.notificationAccessHint(settingsOpened)

    /**
     * Android's permission dialog for [permission] from the activity behind [context]; once the
     * TV no longer shows that dialog, the launcher's own app page in the TV settings instead.
     */
    fun requestPermission(context: Context, permission: String): Boolean {
        var c: Context? = context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        val activity = c as? Activity
        return try {
            val store = SetupStore(context)
            if (activity == null || SetupProber.blocked(activity, store, permission)) {
                dev.tvlauncher.ui.SystemActions.appInfo(context, context.packageName)
            } else {
                store.markAsked(permission)
                activity.requestPermissions(arrayOf(permission), REQ_PERMISSION)
                true
            }
        } catch (e: Throwable) {
            false
        }
    }

    const val REQ_PERMISSION = 7301

    /** Setup on its permissions step (usage access and the other optional access). */
    fun openPermissions(context: Context): Boolean = try {
        context.startActivity(Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(SetupActivity.EXTRA_PAGE, SetupActivity.PAGE_PERMISSIONS))
        true
    } catch (e: Throwable) {
        false
    }

    /** Opens setup where it was left; [restart] starts again from the first page. */
    fun open(context: Context, restart: Boolean = false): Boolean = try {
        context.startActivity(Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(SetupActivity.EXTRA_RESTART, restart))
        true
    } catch (e: Throwable) {
        false
    }

    /**
     * Home's onCreate (after the safe-mode check): applies the automatic configuration in the
     * background and, on the first run only, shows the checklist on top of Home. Home itself is
     * never blocked or delayed.
     */
    fun onHomeCreated(home: Activity, spotlightRequested: Boolean) {
        try {
            autoConfigureAsync(home.applicationContext)
            val store = SetupStore(home)
            if (SetupGate.shouldAutoStart(store.completed, store.autoStarts, spotlightRequested)) {
                store.countAutoStart()
                home.startActivity(Intent(home, SetupActivity::class.java))
            }
        } catch (e: Throwable) {
            // Setup is optional: Home carries on.
        }
    }

    fun autoConfigureAsync(context: Context) {
        val app = context.applicationContext
        try { worker.execute { autoConfigure(app) } } catch (e: Throwable) { }
    }

    /** Runs [SetupAuto.plan] once; returns the applied operations. Safe to call repeatedly. */
    fun autoConfigure(context: Context): List<AutoOp> {
        val store = SetupStore(context)
        val ops = try { SetupAuto.plan(SetupProber.autoInputs(context, store)) } catch (e: Throwable) { return emptyList() }
        val applied = ArrayList<AutoOp>()
        for (op in ops) {
            val ok = try {
                when (op) {
                    is AutoOp.PutSecure -> Settings.Secure.putString(context.contentResolver, op.key, op.value)
                    AutoOp.EnableNowPlaying -> {
                        val d = DiscoverStore(context)
                        d.saveNowPlaying(d.loadNowPlaying().copy(nowPlayingEnabled = true))
                        store.markNowPlayingAutoDone()
                        true
                    }
                    is AutoOp.ConfigureRoom -> {
                        val p = RoomPrefs(context)
                        p.save(p.load().copy(choice = RoomChoice.AUTO,
                            latitudeDeg = RoomSettings.latitude(op.latitude), longitudeDeg = RoomSettings.longitude(op.longitude)))
                        true
                    }
                }
            } catch (e: Throwable) {
                false // e.g. the permission was revoked meanwhile: leave it to the checklist
            }
            if (ok) applied += op
        }
        if (ops.any { it is AutoOp.PutSecure && it.key == SetupAuto.LISTENERS }) store.markAutoTried(StepId.NOW_PLAYING)
        if (ops.any { it is AutoOp.PutSecure && it.key == SetupAuto.A11Y_SERVICES }) store.markAutoTried(StepId.SHORTCUTS)
        return applied
    }
}

/**
 * After a reboot or power cycle: re-applies the automatic configuration (listener, helper,
 * the TV's setup-complete flag) so Home keeps working. It never starts an activity and never
 * fights the system's own home choice.
 */
class SetupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = try { goAsync() } catch (e: Throwable) { null }
        Thread {
            try { SetupStatus.autoConfigure(context.applicationContext) } catch (e: Throwable) { }
            try { pending?.finish() } catch (e: Throwable) { }
        }.start()
    }
}
