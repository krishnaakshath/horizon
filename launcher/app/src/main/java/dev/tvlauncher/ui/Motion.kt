package dev.tvlauncher.ui

/**
 * Process-wide switch for home-screen view animations, set by HomeActivity from the user's
 * animation level (Off, or a system animator scale of 0, turns it off). Durations decided in views
 * go through [ms] so they become 0 (instant) when off.
 */
object Motion {
    @Volatile
    var enabled: Boolean = true

    fun ms(durationMs: Long): Long = if (enabled) durationMs else 0L
}
