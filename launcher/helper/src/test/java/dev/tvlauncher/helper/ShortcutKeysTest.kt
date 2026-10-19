package dev.tvlauncher.helper

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutKeysTest {
    @Test fun rejects_reserved_keys() {
        listOf(
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_UNKNOWN, 0, -1,
        ).forEach { assertFalse("code $it", ShortcutKeys.isAllowed(it)) }
    }

    @Test fun accepts_normal_keys() {
        listOf(
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_BOOKMARK,
        ).forEach { assertTrue("code $it", ShortcutKeys.isAllowed(it)) }
    }
}
