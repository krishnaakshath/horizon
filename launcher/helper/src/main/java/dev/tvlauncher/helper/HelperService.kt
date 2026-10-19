package dev.tvlauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HelperService : AccessibilityService() {
    private var machine: KeyStateMachine? = null
    private var foreground: String? = null
    private var actions: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerActions()
        val prefs = HelperPrefs(this)
        machine = KeyStateMachine(
            now = { SystemClock.uptimeMillis() },
            settings = { prefs.load() },
            goHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            openSpotlight = { openSpotlight() },
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg.contains(".inputmethod")) return
        foreground = pkg
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        actions?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        actions = null
        super.onDestroy()
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

    companion object {
        const val ACTION_POWER_MENU = "dev.tvlauncher.helper.action.POWER_MENU"
        const val PERMISSION = "dev.tvlauncher.permission.HELPER_ACTIONS"
    }
}
