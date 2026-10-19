package dev.tvlauncher.ui

/**
 * D-pad rules for the top-bar pill row (Bluetooth, network, Inputs, Settings).
 * DPAD_DOWN: with an empty shelf there is nothing below, so Down opens Spotlight and a
 * D-pad-only remote always reaches Settings. LEFT/RIGHT stay inside the row. UP from below
 * returns to the pill that had focus last, when it is still shown.
 */
object TopBarNav {
    enum class Action { FOCUS_SHELF, OPEN_SPOTLIGHT }
    enum class Horizontal { MOVE, CONSUME }

    fun onDown(shelfEmpty: Boolean): Action = if (shelfEmpty) Action.OPEN_SPOTLIGHT else Action.FOCUS_SHELF

    /** [index] among the [count] visible pills; the row's ends swallow the key instead of leaking focus. */
    fun onHorizontal(index: Int, count: Int, right: Boolean): Horizontal = when {
        right && index >= count - 1 -> Horizontal.CONSUME
        !right && index <= 0 -> Horizontal.CONSUME
        else -> Horizontal.MOVE
    }

    /** The remembered pill if it is still visible, else null (normal geometric focus search). */
    fun <T> upTarget(remembered: T?, visible: List<T>): T? = remembered?.takeIf { it in visible }
}
