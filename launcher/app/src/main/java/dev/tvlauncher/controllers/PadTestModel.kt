package dev.tvlauncher.controllers

import android.view.KeyEvent
import dev.tvlauncher.input.PadAxes

/** The parts of a controller the test view lights up. */
enum class PadPart { A, B, X, Y, L1, R1, L2, R2, START, SELECT, GUIDE, L3, R3, UP, DOWN, LEFT, RIGHT }

/**
 * State behind "Test controller": which parts are lit and where the sticks point. Buttons light
 * from keys, the D-pad from keys or the hat, L2/R2 from keys or the trigger axes. Holding B for
 * [LEAVE_MS] leaves the page (the remote's BACK always works too). Pure.
 */
class PadTestModel {
    private val keysDown = HashSet<PadPart>()
    var axes = PadAxes(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        private set
    var deviceName: String? = null
        private set
    private var bDownAt = -1L

    fun onKey(keyCode: Int, down: Boolean, name: String?, now: Long) {
        if (name != null) deviceName = name
        val part = partOf(keyCode) ?: return
        if (down) keysDown += part else keysDown -= part
        if (part == PadPart.B) bDownAt = if (down) (if (bDownAt < 0) now else bDownAt) else -1L
    }

    fun onAxes(a: PadAxes, name: String?) {
        if (name != null) deviceName = name
        axes = a
    }

    fun isLit(p: PadPart): Boolean = p in keysDown || when (p) {
        PadPart.LEFT -> axes.hatX < -0.5f
        PadPart.RIGHT -> axes.hatX > 0.5f
        PadPart.UP -> axes.hatY < -0.5f
        PadPart.DOWN -> axes.hatY > 0.5f
        PadPart.L2 -> axes.lt > TRIGGER_LIT
        PadPart.R2 -> axes.rt > TRIGGER_LIT
        else -> false
    }

    /** True once B has been held for [LEAVE_MS]. */
    fun shouldLeave(now: Long): Boolean = bDownAt >= 0 && now - bDownAt >= LEAVE_MS

    companion object {
        const val LEAVE_MS = 900L
        const val TRIGGER_LIT = 0.3f

        fun partOf(keyCode: Int): PadPart? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> PadPart.A
            KeyEvent.KEYCODE_BUTTON_B -> PadPart.B
            KeyEvent.KEYCODE_BUTTON_X -> PadPart.X
            KeyEvent.KEYCODE_BUTTON_Y -> PadPart.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> PadPart.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> PadPart.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> PadPart.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> PadPart.R2
            KeyEvent.KEYCODE_BUTTON_START -> PadPart.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> PadPart.SELECT
            KeyEvent.KEYCODE_BUTTON_MODE -> PadPart.GUIDE
            KeyEvent.KEYCODE_BUTTON_THUMBL -> PadPart.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> PadPart.R3
            KeyEvent.KEYCODE_DPAD_UP -> PadPart.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> PadPart.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> PadPart.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> PadPart.RIGHT
            else -> null
        }
    }
}
