package dev.tvlauncher.system

import android.app.ActivityManager

/**
 * Stress tests (`adb shell monkey`) press random keys for minutes. On a TV run over network adb
 * that must never switch Wi-Fi or Bluetooth off, forget a network or the remote, or move the
 * volume: the test would cut its own connection or leave the TV changed. Same rule as Android's
 * own Settings ([ActivityManager.isUserAMonkey]); the UI still opens and reacts, only the radio,
 * pairing and volume calls are skipped.
 */
object MonkeyGuard {
    /** Tests replace this; the default asks the platform. */
    @Volatile var probe: () -> Boolean = { try { ActivityManager.isUserAMonkey() } catch (e: Throwable) { false } }

    /** True while a monkey run is driving the UI: skip changes to radios, pairings and volume. */
    fun blocks(): Boolean = probe()
}
