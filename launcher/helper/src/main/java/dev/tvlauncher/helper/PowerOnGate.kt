package dev.tvlauncher.helper

import android.view.KeyEvent

/**
 * Pure check before the helper sends HOME for the launcher's "Open home after power on": never
 * when the launcher is already in front, and never while the user is pressing keys (they are
 * already doing something after the wake). Power and wake keys do not count as navigation.
 */
object PowerOnGate {
    const val LAUNCHER_PACKAGE = "dev.tvlauncher"
    const val RECENT_KEY_MS = 6_000L

    private val POWER_KEYS = setOf(
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_TV_POWER
    )

    fun countsAsNavigation(keyCode: Int): Boolean = keyCode !in POWER_KEYS

    /** [lastUserKeyAt] is uptime of the last navigation key, or -1 when none was seen. */
    fun allow(foreground: String?, lastUserKeyAt: Long, now: Long): Boolean {
        if (foreground == LAUNCHER_PACKAGE) return false
        if (lastUserKeyAt >= 0 && now - lastUserKeyAt in 0..RECENT_KEY_MS) return false
        return true
    }
}
