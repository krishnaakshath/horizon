package dev.tvlauncher.network

/**
 * Pure open/closing/closed state with a generation counter. A close animation's end action carries
 * the generation it was started for, so a fast re-open (or [release]) can never be undone by a
 * stale end action.
 */
class PanelLifecycle {
    enum class State { CLOSED, OPEN, CLOSING }

    var state = State.CLOSED
        private set
    var generation = 0
        private set

    val isOpen: Boolean get() = state == State.OPEN
    /** Open or still animating out; BACK should be consumed in both. */
    val isVisible: Boolean get() = state != State.CLOSED

    /** Returns the new generation, or null when already open. A reopen during CLOSING is allowed. */
    fun show(): Int? {
        if (state == State.OPEN) return null
        generation++
        state = State.OPEN
        return generation
    }

    /** Returns the generation to pass to [finishClose], or null when not open. */
    fun dismiss(): Int? {
        if (state != State.OPEN) return null
        state = State.CLOSING
        return generation
    }

    fun finishClose(gen: Int): Boolean {
        if (gen != generation || state != State.CLOSING) return false
        state = State.CLOSED
        return true
    }

    /** Immediate teardown. Returns true if something was visible. Idempotent. */
    fun release(): Boolean {
        val was = state != State.CLOSED
        generation++
        state = State.CLOSED
        return was
    }
}
