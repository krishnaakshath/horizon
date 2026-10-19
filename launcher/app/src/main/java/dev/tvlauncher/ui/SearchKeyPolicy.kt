package dev.tvlauncher.ui

import android.view.KeyEvent

/**
 * Hardware keys on the Spotlight search field. OK-type keys (DPAD_CENTER, ENTER, NUMPAD_ENTER;
 * many remotes send ENTER for OK) open the keyboard when it is hidden. With the keyboard up,
 * ENTER submits a non-empty query; an empty query keeps the keyboard open.
 */
object SearchKeyPolicy {
    enum class Decision { SHOW_IME, SUBMIT_AND_FOCUS_RESULTS, PASS_THROUGH }

    fun isOkKey(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER

    fun decide(keyCode: Int, imeVisible: Boolean, queryEmpty: Boolean): Decision = when {
        !isOkKey(keyCode) -> Decision.PASS_THROUGH
        !imeVisible || queryEmpty -> Decision.SHOW_IME
        else -> Decision.SUBMIT_AND_FOCUS_RESULTS
    }
}
