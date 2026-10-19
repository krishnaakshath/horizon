package dev.tvlauncher.sound

import android.view.KeyEvent

/**
 * Pure home-screen decisions: which [SoundEvent] (if any) a UI happening should play.
 * Whether it is then audible is decided only by [SoundSettings] (enabled, per-event toggles,
 * volume) inside the engine; the animation level plays no part.
 */
object SoundEventMapper {
    /** A focus change counts as a D-pad move only this soon after a D-pad key press. */
    const val DPAD_WINDOW_MS = 300L
    const val NEVER = Long.MIN_VALUE
    const val NOT_READY = Long.MAX_VALUE

    fun isDpadMove(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    /**
     * MOVE for a focus change caused by the user's D-pad, once the first load and splash reveal
     * are over ([readyAtMs]). Programmatic focus (reloads, restoring focus) stays silent.
     */
    fun focusMoved(nowMs: Long, readyAtMs: Long, lastDpadMs: Long): SoundEvent? {
        if (nowMs < readyAtMs || lastDpadMs == NEVER) return null
        val since = nowMs - lastDpadMs
        return if (since in 0..DPAD_WINDOW_MS) SoundEvent.MOVE else null
    }

    fun launch(started: Boolean): SoundEvent = if (started) SoundEvent.LAUNCH else SoundEvent.ERROR

    /** A non-launching OK (top-bar pill, quick action): SELECT, or ERROR when it was rejected. */
    fun action(ok: Boolean): SoundEvent = if (ok) SoundEvent.SELECT else SoundEvent.ERROR

    /** Spotlight / menus: opening plays MENU_OPEN, closing plays BACK. */
    fun panel(opened: Boolean): SoundEvent = if (opened) SoundEvent.MENU_OPEN else SoundEvent.BACK

    /** BACK on the home screen is audible only when it closed or left something. */
    fun back(closedSomething: Boolean): SoundEvent? = if (closedSomething) SoundEvent.BACK else null

    fun stripAction(opensMenu: Boolean): SoundEvent = if (opensMenu) SoundEvent.MENU_OPEN else SoundEvent.SELECT
}
