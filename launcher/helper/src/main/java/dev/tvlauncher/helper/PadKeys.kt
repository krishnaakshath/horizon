package dev.tvlauncher.helper

import android.view.KeyEvent

/**
 * Game controller keys in the accessibility filter. Pure.
 *
 * - The Guide / Xbox / PS button (KEYCODE_BUTTON_MODE) from a controller goes HOME from any app
 *   while the launcher's "Guide button opens Home" is on: the press is consumed and HOME runs
 *   once, on the first down.
 * - A controller's own BACK and volume keys pass straight through: double-BACK and any volume
 *   handling are for the remote (a pad's Select often reports BACK, and games use it).
 * - Any other controller key follows the usual rules (a pad button can be the shortcut key).
 */
object PadKeys {
    private const val SOURCE_GAMEPAD = 0x00000401
    private const val SOURCE_JOYSTICK = 0x01000010

    enum class Verdict {
        /** Not a controller key: the usual remote rules apply. */
        NOT_PAD,
        /** A controller's BACK / volume key: deliver untouched, skip the remote rules. */
        PASS,
        /** Consume (the Guide press already handled, or its release). */
        CONSUME,
        /** Consume and go HOME now. */
        HOME,
    }

    fun isPad(source: Int): Boolean =
        source and SOURCE_GAMEPAD == SOURCE_GAMEPAD || source and SOURCE_JOYSTICK == SOURCE_JOYSTICK

    fun decide(keyCode: Int, source: Int, down: Boolean, repeatCount: Int, guideHome: Boolean): Verdict {
        if (!isPad(source)) return Verdict.NOT_PAD
        if (keyCode in UNTOUCHED) return Verdict.PASS
        if (keyCode != KeyEvent.KEYCODE_BUTTON_MODE) return Verdict.NOT_PAD
        if (!guideHome) return Verdict.PASS
        return if (down && repeatCount == 0) Verdict.HOME else Verdict.CONSUME
    }

    private val UNTOUCHED = setOf(
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE,
    )

    /** "guide=0" in the launcher's controller settings text turns the Guide button off; on otherwise. */
    fun guideHomeFrom(settings: String?): Boolean {
        val part = settings?.split(';')?.firstOrNull { it.trim().startsWith("guide=") } ?: return true
        return part.substringAfter('=').trim() != "0"
    }
}
