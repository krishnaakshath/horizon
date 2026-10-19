package dev.tvlauncher.ui

import android.view.KeyEvent

/** Pure key routing and timing for Spotlight. */
object SpotlightKeys {
    /** External intent action that opens Spotlight (handled in onCreate and onNewIntent). */
    const val ACTION = "dev.tvlauncher.action.SPOTLIGHT"

    enum class TileDown { STRIP, SPOTLIGHT }

    fun isToggleKey(code: Int): Boolean = code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SEARCH

    /** MENU / SEARCH toggle Spotlight, except while the tile menu is on screen. */
    fun togglesSpotlight(code: Int, tileMenuVisible: Boolean): Boolean = isToggleKey(code) && !tileMenuVisible

    /** DPAD_DOWN on a shelf tile enters a visible quick-strip, otherwise opens Spotlight. */
    fun onTileDown(stripVisible: Boolean): TileDown = if (stripVisible) TileDown.STRIP else TileDown.SPOTLIGHT

    fun openMs(full: Boolean): Long = if (full) 240L else 170L

    /**
     * Scrim over the home screen while Spotlight is open: dark enough that shelf tiles under and
     * around the panel recede (70 % FULL, 80 % LITE where the panel has no highlight).
     */
    fun scrimColor(full: Boolean): Int = if (full) 0xE6000000.toInt() else 0xF2000000.toInt()

    enum class Request { OPEN_NOW, DEFER }

    /** Opening before the first app load would show no Browser action and nothing to search. */
    fun onRequest(appsLoaded: Boolean): Request = if (appsLoaded) Request.OPEN_NOW else Request.DEFER
}
