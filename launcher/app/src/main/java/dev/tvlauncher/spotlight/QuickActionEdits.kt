package dev.tvlauncher.spotlight

import dev.tvlauncher.ui.QuickActions.Item

/** Pure edits behind Settings > Spotlight > Quick actions (one row per action). */
object QuickActionEdits {
    /** Shows or hides [item]; its place in the order is kept. */
    fun setShown(c: SpotlightConfig, item: Item, shown: Boolean): SpotlightConfig =
        c.copy(hidden = if (shown) c.hidden - item else c.hidden + item)

    /**
     * Moves [item] [delta] places (LEFT = -1 = earlier in the row, RIGHT = +1 = later). Null when
     * it is already at that end, so the page can say so instead of doing nothing silently.
     */
    fun move(c: SpotlightConfig, item: Item, delta: Int): SpotlightConfig? {
        val i = c.order.indexOf(item)
        val j = i + delta
        if (i < 0 || j !in c.order.indices || delta == 0) return null
        val list = c.order.toMutableList()
        list.removeAt(i)
        list.add(j, item)
        return c.copy(order = list)
    }

    /** Only the actions part back to default (keys and behavior unchanged). */
    fun resetActions(c: SpotlightConfig): SpotlightConfig =
        c.copy(order = SpotlightConfig.DEFAULT_ORDER, hidden = SpotlightConfig.DEFAULT_HIDDEN)

    /** "3rd of 9 shown" for a shown action, "Hidden" otherwise. */
    fun subtitle(c: SpotlightConfig, item: Item, unavailable: String?): String {
        if (item in c.hidden) return unavailable ?: "Hidden"
        val shown = c.shown
        val pos = shown.indexOf(item) + 1
        val base = "${ordinal(pos)} of ${shown.size} in the row"
        return if (unavailable != null) "$base · $unavailable" else base
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** Main-page value: "10 shown". */
    fun summary(c: SpotlightConfig): String = c.shown.size.let { if (it == 0) "None shown" else "$it shown" }
}
