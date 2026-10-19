package dev.tvlauncher.ui

import android.view.KeyEvent

/** Pure key routing and timing for Spotlight. */
object SpotlightKeys {
    /** External intent action that opens Spotlight (handled in onCreate and onNewIntent). */
    const val ACTION = "dev.tvlauncher.action.SPOTLIGHT"

    enum class TileDown { STRIP, SPOTLIGHT }

    fun isToggleKey(code: Int): Boolean = code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SEARCH

    /** DPAD_DOWN on a shelf tile enters a visible quick-strip, otherwise opens Spotlight. */
    fun onTileDown(stripVisible: Boolean): TileDown = if (stripVisible) TileDown.STRIP else TileDown.SPOTLIGHT

    fun openMs(full: Boolean): Long = if (full) 240L else 170L
}
