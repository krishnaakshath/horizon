package dev.tvlauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HelperService : AccessibilityService() {
    private lateinit var prefs: HelperPrefs
    private var foreground: String? = null
    private var detector: DoubleBackDetector? = null
    private var detectorWindow = -1L
    private var swallowBackUp = false
    private var swallowShortcutUp = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = HelperPrefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg.contains(".inputmethod")) return
        foreground = pkg
    }

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::prefs.isInitialized) return false
        val settings = prefs.load()
        val code = event.keyCode
        return when {
            code == KeyEvent.KEYCODE_BACK -> handleBack(event, settings)
            settings.shortcutKey != 0 && code == settings.shortcutKey -> handleShortcut(event)
            else -> false
        }
    }

    private fun handleBack(event: KeyEvent, s: HelperSettings): Boolean {
        val up = event.action == KeyEvent.ACTION_UP
        if (!s.doubleBackEnabled || ExclusionList.isExcluded(foreground, s.exclusions)) {
            detector?.reset()
            // Finish consuming a press whose DOWN we already swallowed.
            if (up && swallowBackUp) {
                swallowBackUp = false
                return true
            }
            return false
        }
        val d = detectorFor(s.windowMs)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0 && d.isArmed()) swallowBackUp = true
            return swallowBackUp
        }
        if (!up) return false
        val second = d.onBackUp()
        if (swallowBackUp || second) {
            swallowBackUp = false
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }
        return false
    }

    private fun detectorFor(windowMs: Long): DoubleBackDetector {
        val existing = detector
        if (existing != null && detectorWindow == windowMs) return existing
        detectorWindow = windowMs
        return DoubleBackDetector(windowMs) { System.currentTimeMillis() }.also { detector = it }
    }

    private fun handleShortcut(event: KeyEvent): Boolean {
        if (foreground == packageName) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            swallowShortcutUp = true
            return true
        }
        if (event.action != KeyEvent.ACTION_UP) return false
        swallowShortcutUp = false
        try {
            startActivity(
                Intent("dev.tvlauncher.action.SPOTLIGHT")
                    .setPackage("dev.tvlauncher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        } catch (e: Exception) {
            // Launcher missing or not handling the action: do nothing.
        }
        return true
    }
}
