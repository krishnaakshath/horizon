package dev.tvlauncher.ui

/**
 * What the dock shows, in focus order. Two buttons today; [MAX_ITEMS] leaves a slot for a
 * future Browser button (not implemented). The remote's mic key already opens the Assistant and
 * the app row is the all-apps view, so neither has a dock button.
 */
object DockItems {
    enum class Item(val hasLongPress: Boolean) {
        INPUTS(false),
        /** Long-press: "Unhide all apps". */
        SETTINGS(true)
    }

    const val MAX_ITEMS = 3
    val items: List<Item> = listOf(Item.INPUTS, Item.SETTINGS)
    const val firstFocusIndex = 0
}
