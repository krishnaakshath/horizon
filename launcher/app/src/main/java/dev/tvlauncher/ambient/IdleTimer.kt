package dev.tvlauncher.ambient

/**
 * Pure idle state machine. Idle once [timeoutMs] passed since the last activity while running
 * and not paused (Spotlight, dialogs, panels). Pausing then resuming restarts the full timeout.
 * [consumeIdle] fires once per idle period so the host enters ambient only once.
 */
class IdleTimer(timeoutMs: Long, private val clock: () -> Long) {
    private var timeout = timeoutMs
    private var last = clock()
    private var running = true
    private var fired = false
    private var pauses = 0

    fun onActivity() {
        last = clock()
        fired = false
    }

    fun setPaused(flag: Int, paused: Boolean) {
        val before = pauses
        pauses = if (paused) pauses or flag else pauses and flag.inv()
        if (before != 0 && pauses == 0) last = clock()
    }

    fun start() {
        running = true
        fired = false
        last = clock()
    }

    fun stop() { running = false }

    fun setTimeout(ms: Long) { timeout = ms }

    private fun armed(): Boolean = running && timeout > 0 && pauses == 0 && !fired

    fun isIdle(): Boolean = armed() && clock() - last >= timeout

    /** ms until idle, or -1 when disabled, paused, stopped or already fired. */
    fun remainingMs(): Long {
        if (!armed()) return -1L
        val elapsed = (clock() - last).coerceAtLeast(0)
        return (timeout - elapsed).coerceAtLeast(0)
    }

    fun consumeIdle(): Boolean {
        if (!isIdle()) return false
        fired = true
        return true
    }

    companion object {
        const val PAUSE_SPOTLIGHT = 1
        const val PAUSE_DIALOG = 2
        const val PAUSE_PANEL = 4
    }
}
