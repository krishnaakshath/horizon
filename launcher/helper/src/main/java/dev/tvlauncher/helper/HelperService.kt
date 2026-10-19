package dev.tvlauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HelperService : AccessibilityService() {
    private var machine: KeyStateMachine? = null
    private var foreground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
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
}
