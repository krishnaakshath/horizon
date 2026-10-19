package dev.tvlauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HelperService : AccessibilityService() {
    private var machine: KeyStateMachine? = null
    private var foreground: String? = null
    @Volatile private var lastUserKeyAt = -1L
    private var actions: BroadcastReceiver? = null
    /** The launcher's "Guide button opens Home" (Settings > System > Controllers); on by default. */
    @Volatile private var guideHome = true
    private var padObserver: ContentObserver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        registerActions()
        val prefs = HelperPrefs(this)
        machine = KeyStateMachine(
            now = { SystemClock.uptimeMillis() },
            settings = { prefs.load() },
            goHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            openSpotlight = { openSpotlight() },
        )
        // Ask the launcher for its Spotlight key (it pushes only when ours is stale).
        SpotlightSync.sendState(this, hello = true, serviceOn = true)
        watchPadSettings()
    }

    private fun watchPadSettings() {
        readPadSettings()
        if (padObserver != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = readPadSettings()
        }
        try {
            contentResolver.registerContentObserver(PAD_SETTINGS, false, o)
            padObserver = o
        } catch (e: Exception) {
            // Launcher missing: the Guide button keeps its default.
        }
    }

    private fun readPadSettings() {
        Thread {
            val text = try {
                contentResolver.query(PAD_SETTINGS, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (e: Exception) {
                null
            }
            guideHome = PadKeys.guideHomeFrom(text)
        }.apply { isDaemon = true }.start()
    }

    private fun unwatchPadSettings() {
        padObserver?.let { try { contentResolver.unregisterContentObserver(it) } catch (e: Exception) { } }
        padObserver = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg.contains(".inputmethod")) return
        foreground = pkg
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        SpotlightSync.sendState(this, hello = false, serviceOn = false)
        unwatchPadSettings()
        if (current === this) current = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unwatchPadSettings()
        if (current === this) current = null
        actions?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        actions = null
        super.onDestroy()
    }

    /** The launcher's "Open home after power on": HOME as if pressed on the remote, unless [PowerOnGate] says no. */
    fun homeAfterPowerOn(): Boolean {
        if (!PowerOnGate.allow(foreground, lastUserKeyAt, SystemClock.uptimeMillis())) return false
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * The launcher's Power page asks for the TV's power menu here (apps cannot open it themselves).
     * Only senders holding the signature permission (the launcher) reach this receiver.
     */
    private fun registerActions() {
        if (actions != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == ACTION_POWER_MENU) {
                    try { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) } catch (e: Exception) { }
                }
            }
        }
        try {
            registerReceiver(r, IntentFilter(ACTION_POWER_MENU), PERMISSION, null)
            actions = r
        } catch (e: Exception) {
            // No power menu from the launcher this session.
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return try {
            // Game controllers: Guide goes HOME (if allowed); a pad's BACK / volume keys are the
            // app's (no double-BACK); a pad press still counts as someone using the TV.
            if (PadKeys.isPad(event.source) && event.action == KeyEvent.ACTION_DOWN) lastUserKeyAt = SystemClock.uptimeMillis()
            when (PadKeys.decide(event.keyCode, event.source, event.action == KeyEvent.ACTION_DOWN, event.repeatCount, guideHome)) {
                PadKeys.Verdict.NOT_PAD -> Unit
                PadKeys.Verdict.PASS -> return false
                PadKeys.Verdict.CONSUME -> return true
                PadKeys.Verdict.HOME -> { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            if (event.action == KeyEvent.ACTION_DOWN && PowerOnGate.countsAsNavigation(event.keyCode)) {
                lastUserKeyAt = SystemClock.uptimeMillis()
            }
            val machine = machine ?: return false
            val action = when (event.action) {
                KeyEvent.ACTION_DOWN -> KeyAction.DOWN
                KeyEvent.ACTION_UP -> KeyAction.UP
                else -> return false
            }
            val input = KeyInput(
                event.keyCode,
                action,
                event.repeatCount,
                (event.flags and KeyEvent.FLAG_CANCELED) != 0,
                foreground,
            )
            machine.handle(input) == Decision.CONSUME
        } catch (e: Throwable) {
            machine?.reset()
            false
        }
    }

    companion object {
        /** The launcher's controller settings (read-only provider). */
        private val PAD_SETTINGS: Uri = Uri.parse("content://dev.tvlauncher.gamepad/settings")

        /** The connected service, for [PowerOnHomeReceiver]; null while the service is off. */
        @Volatile
        var current: HelperService? = null
            private set

        const val ACTION_POWER_MENU = "dev.tvlauncher.helper.action.POWER_MENU"
        const val PERMISSION = "dev.tvlauncher.permission.HELPER_ACTIONS"
    }

    private fun openSpotlight() {
        try {
            startActivity(
                Intent("dev.tvlauncher.action.SPOTLIGHT")
                    .setPackage("dev.tvlauncher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (e: Exception) {
            // Launcher missing or not handling the action: do nothing.
        }
    }

}
