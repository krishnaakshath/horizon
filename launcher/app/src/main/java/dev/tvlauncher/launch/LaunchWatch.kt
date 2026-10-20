package dev.tvlauncher.launch

/**
 * The state machine of the app-launch splash. Pure (times are passed in), so it is JVM-testable.
 *
 *  IDLE --begin--> MOTION --motionEnded--> WAITING --(LOADER_DELAY_MS after begin)--> LOADER
 *  any state --stop (home stopped: the app is up; or BACK/HOME)--> IDLE (dismiss)
 *  any state --(HARD_TIMEOUT_MS after begin)--> IDLE (dismiss): the overlay can never stay stuck.
 *
 * The loader appears only for apps slower than [LOADER_DELAY_MS]; fast apps never show it.
 */
class LaunchWatch(
    val loaderDelayMs: Long = LOADER_DELAY_MS,
    val hardTimeoutMs: Long = HARD_TIMEOUT_MS
) {
    enum class State { IDLE, MOTION, WAITING, LOADER }
    enum class Action { NONE, SHOW_LOADER, DISMISS }

    var state: State = State.IDLE
        private set
    private var begunAt = 0L

    fun begin(now: Long) {
        state = State.MOTION
        begunAt = now
    }

    /** The expand motion finished; the app has been asked to start. */
    fun motionEnded(now: Long): Action {
        if (state != State.MOTION) return Action.NONE
        state = State.WAITING
        return tick(now)
    }

    /** Re-evaluates at [now]; call at [nextAt]. */
    fun tick(now: Long): Action {
        if (state == State.IDLE) return Action.NONE
        val elapsed = now - begunAt
        if (elapsed >= hardTimeoutMs) {
            state = State.IDLE
            return Action.DISMISS
        }
        if (state == State.WAITING && elapsed >= loaderDelayMs) {
            state = State.LOADER
            return Action.SHOW_LOADER
        }
        return Action.NONE
    }

    /** When [tick] next has something to decide, or null when idle. */
    fun nextAt(): Long? = when (state) {
        State.IDLE -> null
        State.MOTION, State.LOADER -> begunAt + hardTimeoutMs
        State.WAITING -> begunAt + loaderDelayMs
    }

    /** Home stopped, a key, or a new launch: dismiss whatever shows. */
    fun stop(): Action {
        val was = state
        state = State.IDLE
        return if (was == State.IDLE) Action.NONE else Action.DISMISS
    }

    companion object {
        const val LOADER_DELAY_MS = 600L
        const val HARD_TIMEOUT_MS = 4000L
    }
}
