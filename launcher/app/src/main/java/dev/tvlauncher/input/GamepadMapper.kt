package dev.tvlauncher.input

import android.view.KeyEvent

/** What a controller button asks the screen to do (beyond plain OK / BACK, which are keys). */
enum class PadCommand {
    /** X / Square: the focused item's options (its long-press). */
    OPTIONS,
    /** Y / Triangle: Spotlight search. */
    SEARCH,
    /** Start / Options: Spotlight. */
    START,
    /** Select / View / Share: the launcher's Settings. */
    SELECT,
    /** Guide / Xbox / PS: the home screen. */
    HOME,
    /** L1 / R1: a page (or Settings section) back / forward. */
    PAGE_PREV, PAGE_NEXT,
    /** L2 / R2: a page up / down. */
    SCROLL_UP, SCROLL_DOWN,
}

/** The mapper's answer for one key event. */
sealed class PadKeyResult {
    /** Not a controller key (the remote, a keyboard): deliver untouched. */
    object Pass : PadKeyResult() { override fun toString() = "Pass" }
    /** Drop it (a duplicate, or the second half of a command). */
    object Swallow : PadKeyResult() { override fun toString() = "Swallow" }
    /** Deliver as [keyCode] instead (same action, timing and repeat count). */
    data class Translate(val keyCode: Int) : PadKeyResult()
    /** Run [command]; [repeat] > 0 while the button is held (only page / scroll repeat). */
    data class Command(val command: PadCommand, val repeat: Int) : PadKeyResult()
}

/** Input source bits (android.view.InputDevice), kept here so the rules stay JVM-testable. */
object PadSource {
    const val GAMEPAD = 0x00000401
    const val JOYSTICK = 0x01000010
    const val DPAD = 0x00000201

    fun isPad(source: Int): Boolean = source and GAMEPAD == GAMEPAD || source and JOYSTICK == JOYSTICK
}

/**
 * Which half of a duplicated control a controller really uses. Many pads report their D-pad both
 * as KEYCODE_DPAD_* keys and as the hat axes (and L2/R2 both as keys and as trigger axes); acting
 * on both would move focus twice. Per device and [Channel], whichever form arrives first wins for
 * good: a pad that sends keys has its hat ignored, a pad whose hat came first has its D-pad keys
 * swallowed. Pure.
 */
class InputArbiter {
    enum class Channel { DPAD, TRIGGER }
    private enum class Form { KEYS, AXES }

    private val forms = HashMap<Pair<Int, Channel>, Form>()

    /** A key of [channel] from [device]: true = deliver it. */
    fun useKeys(device: Int, channel: Channel): Boolean = forms.getOrPut(device to channel) { Form.KEYS } == Form.KEYS

    /**
     * Axis data of [channel] from [device]; [active] = off its rest position. True = act on the
     * axes. Resting axes never claim the channel (a pad that only sends keys still reports a
     * centred hat now and then).
     */
    fun useAxes(device: Int, channel: Channel, active: Boolean): Boolean {
        val f = forms[device to channel]
        if (f == null) {
            if (!active) return false
            forms[device to channel] = Form.AXES
            return true
        }
        return f == Form.AXES
    }

    fun forget(device: Int) {
        forms.keys.removeAll { it.first == device }
    }
}

/**
 * Pure controller button rules, one call per key event:
 *
 * - A / Cross = OK (DPAD_CENTER), B / Circle = BACK; [GamepadSettings.swapAB] swaps them
 *   (Nintendo layout). While a text field has focus both pass through untouched, so the system's
 *   own controller fallbacks reach the on-screen keyboard.
 * - X = [PadCommand.OPTIONS], Y = SEARCH, Start = START, Select = SELECT, Guide = HOME (only with
 *   [GamepadSettings.guideHome]; otherwise untouched), L1/R1 = PAGE_PREV/NEXT, L2/R2 =
 *   SCROLL_UP/DOWN. Commands fire on the press; page and scroll also on held repeats; the release
 *   is swallowed.
 * - D-pad keys from a controller go through [InputArbiter] (hat duplicates). D-pad keys from the
 *   remote or a keyboard are never touched.
 * - The release of a press always gets the same treatment as its press (a setting changed
 *   in between cannot split a press into OK-down / BACK-up); a release whose press was never
 *   seen is swallowed.
 */
class GamepadMapper(
    private val settings: () -> GamepadSettings = { GamepadSettings() },
    val arbiter: InputArbiter = InputArbiter(),
) {
    private val pressed = HashMap<Long, PadKeyResult>()

    fun onKey(keyCode: Int, down: Boolean, repeat: Int, source: Int, deviceId: Int, textEditing: Boolean = false): PadKeyResult {
        val fromPad = PadSource.isPad(source)
        if (keyCode in DPAD_KEYS) {
            if (!fromPad) return PadKeyResult.Pass
            return if (arbiter.useKeys(deviceId, InputArbiter.Channel.DPAD)) PadKeyResult.Pass else PadKeyResult.Swallow
        }
        if (!fromPad && !isButton(keyCode)) return PadKeyResult.Pass
        if (!isButton(keyCode)) return PadKeyResult.Pass // e.g. a pad's own BACK or volume key
        if ((keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2) &&
            !arbiter.useKeys(deviceId, InputArbiter.Channel.TRIGGER)
        ) return PadKeyResult.Swallow

        val slot = (deviceId.toLong() shl 32) or (keyCode.toLong() and 0xffffffffL)
        if (down && repeat == 0) {
            val r = resolve(keyCode, textEditing)
            pressed[slot] = r
            return r
        }
        val first = pressed[slot] ?: return PadKeyResult.Swallow
        if (!down) pressed.remove(slot)
        return when (first) {
            is PadKeyResult.Command -> if (down && first.command in REPEATING) PadKeyResult.Command(first.command, repeat) else PadKeyResult.Swallow
            else -> first
        }
    }

    private fun resolve(keyCode: Int, textEditing: Boolean): PadKeyResult {
        val s = settings()
        fun cmd(c: PadCommand) = PadKeyResult.Command(c, 0)
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A ->
                if (textEditing) PadKeyResult.Pass else PadKeyResult.Translate(if (s.swapAB) KeyEvent.KEYCODE_BACK else KeyEvent.KEYCODE_DPAD_CENTER)
            KeyEvent.KEYCODE_BUTTON_B ->
                if (textEditing) PadKeyResult.Pass else PadKeyResult.Translate(if (s.swapAB) KeyEvent.KEYCODE_DPAD_CENTER else KeyEvent.KEYCODE_BACK)
            KeyEvent.KEYCODE_BUTTON_X -> cmd(PadCommand.OPTIONS)
            KeyEvent.KEYCODE_BUTTON_Y -> cmd(PadCommand.SEARCH)
            KeyEvent.KEYCODE_BUTTON_START -> cmd(PadCommand.START)
            KeyEvent.KEYCODE_BUTTON_SELECT -> cmd(PadCommand.SELECT)
            KeyEvent.KEYCODE_BUTTON_MODE -> if (s.guideHome) cmd(PadCommand.HOME) else PadKeyResult.Pass
            KeyEvent.KEYCODE_BUTTON_L1 -> cmd(PadCommand.PAGE_PREV)
            KeyEvent.KEYCODE_BUTTON_R1 -> cmd(PadCommand.PAGE_NEXT)
            KeyEvent.KEYCODE_BUTTON_L2 -> cmd(PadCommand.SCROLL_UP)
            KeyEvent.KEYCODE_BUTTON_R2 -> cmd(PadCommand.SCROLL_DOWN)
            else -> PadKeyResult.Pass // stick clicks, C/Z, numbered buttons: the system defaults
        }
    }

    /** Forgets a device (disconnected) and any press it had open. */
    fun forget(deviceId: Int) {
        arbiter.forget(deviceId)
        pressed.keys.removeAll { (it shr 32).toInt() == deviceId }
    }

    companion object {
        val DPAD_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
        )
        private val REPEATING = setOf(PadCommand.PAGE_PREV, PadCommand.PAGE_NEXT, PadCommand.SCROLL_UP, PadCommand.SCROLL_DOWN)

        /** KEYCODE_BUTTON_A (96) .. KEYCODE_BUTTON_MODE (110), and BUTTON_1..16 (188..203). */
        fun isButton(keyCode: Int): Boolean = keyCode in 96..110 || keyCode in 188..203

        fun keyCodeOf(d: PadDir): Int = when (d) {
            PadDir.UP -> KeyEvent.KEYCODE_DPAD_UP
            PadDir.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            PadDir.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            PadDir.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        }
    }
}
