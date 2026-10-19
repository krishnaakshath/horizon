package dev.tvlauncher.input

import kotlin.math.abs
import kotlin.math.sqrt

/** A D-pad direction produced from a stick, a hat or a trigger. */
enum class PadDir { UP, DOWN, LEFT, RIGHT }

/** One synthetic key step: [kind] DOWN starts a press, REPEAT continues it, UP ends it. */
data class NavStep(val dir: PadDir, val kind: Kind, val repeat: Int = 0) {
    enum class Kind { DOWN, REPEAT, UP }
}

/**
 * Key-repeat timing for a held stick: the first repeat after [INITIAL_MS], then it speeds up
 * through [RAMP_MS] and settles at [STEADY_MS]. Pure: callers pass the clock.
 */
object RepeatTimeline {
    const val INITIAL_MS = 350L
    val RAMP_MS = longArrayOf(150L, 120L, 100L)
    const val STEADY_MS = 90L

    /** Delay before repeat number [repeat] + 1, after repeat [repeat] (0 = the initial press). */
    fun delayAfter(repeat: Int): Long = when {
        repeat <= 0 -> INITIAL_MS
        repeat - 1 < RAMP_MS.size -> RAMP_MS[repeat - 1]
        else -> STEADY_MS
    }
}

/**
 * Turns an analog stick (or a hat, whose values are only -1, 0 and 1) into D-pad presses:
 * radial [StickTuning.deadzone] against drift, a press threshold with hysteresis, one direction
 * at a time (the dominant axis; switching needs a clear lead so a diagonal does not flicker) and
 * [RepeatTimeline] repeats while held. Pure and clock-free: [update] on every axis change,
 * [tick] when [nextDueAt] arrives.
 */
class StickNavigator(private val tuning: () -> StickTuning = { StickTuning.forSensitivity(GamepadSettings.NORMAL) }) {
    var held: PadDir? = null
        private set
    private var repeats = 0
    private var dueAt = 0L
    private var lastX = 0f
    private var lastY = 0f

    /** When the next repeat is due, or null while nothing is held. */
    fun nextDueAt(): Long? = if (held != null) dueAt else null

    fun update(x: Float, y: Float, now: Long): List<NavStep> {
        val t = tuning()
        val mag = sqrt(x * x + y * y)
        val (cx, cy) = if (mag < t.deadzone) 0f to 0f else x to y
        lastX = cx; lastY = cy
        val out = ArrayList<NavStep>(2)
        val cur = held
        if (cur != null) {
            val along = along(cur, cx, cy)
            val best = dominant(cx, cy)
            val bestValue = best?.let { along(it, cx, cy) } ?: 0f
            val switch = best != null && best != cur && bestValue >= t.press && bestValue > along + SWITCH_LEAD
            if (along < t.release || switch) {
                out += NavStep(cur, NavStep.Kind.UP, repeats)
                held = null
            } else {
                return out
            }
        }
        val next = dominant(cx, cy) ?: return out
        if (along(next, cx, cy) >= t.press) {
            held = next
            repeats = 0
            dueAt = now + RepeatTimeline.delayAfter(0)
            out += NavStep(next, NavStep.Kind.DOWN, 0)
        }
        return out
    }

    /** Repeats that are due at [now] (at most one per call; callers re-arm with [nextDueAt]). */
    fun tick(now: Long): List<NavStep> {
        val cur = held ?: return emptyList()
        if (now < dueAt) return emptyList()
        repeats++
        dueAt = now + RepeatTimeline.delayAfter(repeats)
        return listOf(NavStep(cur, NavStep.Kind.REPEAT, repeats))
    }

    /** Ends a held press (pause, focus loss); the UP to deliver, if any. */
    fun reset(): List<NavStep> {
        val cur = held ?: return emptyList()
        held = null
        lastX = 0f; lastY = 0f
        return listOf(NavStep(cur, NavStep.Kind.UP, repeats))
    }

    private fun dominant(x: Float, y: Float): PadDir? = when {
        x == 0f && y == 0f -> null
        abs(x) >= abs(y) -> if (x > 0) PadDir.RIGHT else PadDir.LEFT
        else -> if (y > 0) PadDir.DOWN else PadDir.UP // Android: +Y is down
    }

    private fun along(d: PadDir, x: Float, y: Float): Float = when (d) {
        PadDir.RIGHT -> x
        PadDir.LEFT -> -x
        PadDir.DOWN -> y
        PadDir.UP -> -y
    }

    companion object {
        /** How much more the other axis must show before a held direction switches to it. */
        const val SWITCH_LEAD = 0.15f
    }
}

/**
 * An analog trigger used as a button (L2/R2 on pads that report them only as axes): pressed above
 * [PRESS], released under [RELEASE], with [RepeatTimeline] repeats while held. Pure.
 */
class AxisButton {
    var pressed = false
        private set
    private var repeats = 0
    private var dueAt = 0L

    /** Returns the repeat count of a new press/repeat (0 = press), or null when nothing fires. */
    fun update(value: Float, now: Long): Int? {
        if (!pressed && value >= PRESS) {
            pressed = true
            repeats = 0
            dueAt = now + RepeatTimeline.delayAfter(0)
            return 0
        }
        if (pressed && value < RELEASE) pressed = false
        return null
    }

    fun nextDueAt(): Long? = if (pressed) dueAt else null

    fun tick(now: Long): Int? {
        if (!pressed || now < dueAt) return null
        repeats++
        dueAt = now + RepeatTimeline.delayAfter(repeats)
        return repeats
    }

    fun reset() { pressed = false }

    companion object {
        const val PRESS = 0.6f
        const val RELEASE = 0.3f
    }
}

/** Right-stick scrolling speed: nothing inside the deadzone, then a soft (squared) curve. Pure. */
object AnalogScroll {
    /** Pixels per second at full tilt, in dp (callers multiply by density). */
    const val MAX_DP_PER_SEC = 1600f

    /** Signed speed for axis value [v]; 0 inside [deadzone]. */
    fun velocity(v: Float, deadzone: Float = StickTuning.DEADZONE, max: Float = MAX_DP_PER_SEC): Float {
        val a = abs(v).coerceAtMost(1f)
        if (a < deadzone) return 0f
        val n = (a - deadzone) / (1f - deadzone)
        return (if (v < 0) -1f else 1f) * n * n * max
    }
}

/** Whether a controller sample is a deliberate push (wakes ambient), not drift or a resting axis. Pure. */
object PadWake {
    const val STICK = 0.5f
    const val TRIGGER = 0.6f

    fun deliberate(a: PadAxes): Boolean =
        abs(a.lx) >= STICK || abs(a.ly) >= STICK || abs(a.rx) >= STICK || abs(a.ry) >= STICK ||
            a.hatX != 0f || a.hatY != 0f || a.lt >= TRIGGER || a.rt >= TRIGGER
}
