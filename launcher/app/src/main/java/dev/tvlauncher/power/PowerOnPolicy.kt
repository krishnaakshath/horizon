package dev.tvlauncher.power

/** What woke the launcher up: a cold boot or a wake from standby (TCL: suspend-to-RAM resume). */
enum class PowerOnTrigger { BOOT, WAKE }

/**
 * What is known about the screen at decision time. Every field may be unknown (null): the launcher
 * sees the foreground only through usage access or the Remote helper.
 */
data class PowerOnSnapshot(
    val enabled: Boolean,
    val isHomeApp: Boolean,
    /** False while safe mode (CrashGuard) is tripped: then the launcher never pushes itself forward. */
    val safeModeClear: Boolean,
    /** Our HomeActivity is resumed right now (from this process's own lifecycle), or null if unknown. */
    val homeInFront: Boolean? = null,
    /** Foreground package, or null if unknown. */
    val foreground: String? = null,
    /** The user opened something after the wake (e.g. an app key woke the TV), or null if unknown. */
    val navigatedAfterWake: Boolean? = null
)

/** Persisted policy state (elapsedRealtime values; a smaller "now" later means a reboot). */
data class PowerOnState(
    val armedAt: Long = -1L,
    val trigger: PowerOnTrigger? = null,
    val done: Boolean = false,
    /** Times we actually brought home forward, newest last (loop guard). */
    val fires: List<Long> = emptyList()
)

sealed class PowerOnDecision {
    object GoHome : PowerOnDecision() { override fun toString() = "GoHome" }
    data class Skip(val reason: Reason) : PowerOnDecision()

    enum class Reason {
        NOT_ARMED, ALREADY_DONE, TOO_LATE, DISABLED, NOT_HOME_APP, SAFE_MODE,
        USER_NAVIGATED, ALREADY_HOME, LOOP_GUARD
    }
}

/**
 * Pure "open home after power on" state machine.
 *
 * - [arm] on every boot/wake signal. Signals that arrive within [SAME_WAKE_MS] of the first belong
 *   to the same wake (TCL sends SCREEN_ON and STR_RESUME_COMPLETED, the latter sometimes twice).
 * - [decide] acts at most once per wake ([PowerOnState.done]), only within [WINDOW_MS] of the wake,
 *   never when disabled, when another app is home, while safe mode is tripped, when the user already
 *   opened something after the wake, or when home is already in front.
 * - Loop guard: at most [MAX_FIRES] home pushes within [FIRE_WINDOW_MS], whatever the signals do.
 *
 * Never throws; the Android glue persists [PowerOnState] between calls.
 */
object PowerOnPolicy {
    const val SAME_WAKE_MS = 20_000L
    const val WINDOW_MS = 15_000L
    const val MAX_FIRES = 3
    const val FIRE_WINDOW_MS = 10 * 60_000L

    fun arm(state: PowerOnState, trigger: PowerOnTrigger, now: Long): PowerOnState {
        val rebooted = state.armedAt >= 0 && now < state.armedAt
        val fires = if (rebooted) emptyList() else state.fires.filter { now - it in 0 until FIRE_WINDOW_MS }
        if (!rebooted && state.armedAt >= 0 && now - state.armedAt < SAME_WAKE_MS) {
            // Same wake: keep the first arm time and the done flag; a BOOT signal wins over WAKE.
            val t = if (trigger == PowerOnTrigger.BOOT) PowerOnTrigger.BOOT else state.trigger ?: trigger
            return state.copy(trigger = t, fires = fires)
        }
        return PowerOnState(armedAt = now, trigger = trigger, done = false, fires = fires)
    }

    /** The decision and the state to persist (done is set whenever this wake is settled). */
    fun decide(state: PowerOnState, s: PowerOnSnapshot, now: Long): Pair<PowerOnDecision, PowerOnState> {
        fun skip(r: PowerOnDecision.Reason, settle: Boolean) =
            PowerOnDecision.Skip(r) to (if (settle) state.copy(done = true) else state)

        if (state.armedAt < 0 || now < state.armedAt) return skip(PowerOnDecision.Reason.NOT_ARMED, false)
        if (state.done) return skip(PowerOnDecision.Reason.ALREADY_DONE, false)
        if (now - state.armedAt > WINDOW_MS) return skip(PowerOnDecision.Reason.TOO_LATE, true)
        if (!s.enabled) return skip(PowerOnDecision.Reason.DISABLED, true)
        if (!s.isHomeApp) return skip(PowerOnDecision.Reason.NOT_HOME_APP, true)
        if (!s.safeModeClear) return skip(PowerOnDecision.Reason.SAFE_MODE, true)
        if (s.navigatedAfterWake == true) return skip(PowerOnDecision.Reason.USER_NAVIGATED, true)
        if (s.homeInFront == true) return skip(PowerOnDecision.Reason.ALREADY_HOME, true)
        val recent = state.fires.filter { now - it in 0 until FIRE_WINDOW_MS }
        if (recent.size >= MAX_FIRES) return skip(PowerOnDecision.Reason.LOOP_GUARD, true)
        return PowerOnDecision.GoHome to state.copy(done = true, fires = (recent + now).takeLast(MAX_FIRES))
    }
}
