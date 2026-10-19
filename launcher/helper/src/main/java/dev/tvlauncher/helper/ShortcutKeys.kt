package dev.tvlauncher.helper

import android.view.KeyEvent

object ShortcutKeys {
    private val forbidden = setOf(
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP_LEFT,
        KeyEvent.KEYCODE_DPAD_UP_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE,
    )

    fun isAllowed(keyCode: Int): Boolean = keyCode > 0 && keyCode !in forbidden
}
