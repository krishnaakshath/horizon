package dev.tvlauncher.discover

/** Pure spec of the end-of-shelf "Get more apps" tile. */
object DiscoverTileSpec {
    const val KEY = "discover:get-more-apps"
    const val LABEL = "Get more apps"
    const val CONTENT_DESCRIPTION = "Get more apps: browse popular apps"
    /** Dashed outline: dash and gap length, stroke width (dp). */
    const val DASH_DP = 8
    const val DASH_GAP_DP = 6
    const val STROKE_DP = 2
    const val PLUS_DP = 40
    const val OUTLINE = 0x66FFFFFF
    const val OUTLINE_FOCUSED = 0xFFFFFFFF.toInt()
    const val FILL = 0x0DFFFFFF
    const val FILL_FOCUSED = 0x26FFFFFF

    /** Append after the last app tile unless the user turned it off or is rearranging tiles. */
    fun shouldAppend(settings: DiscoverSettings, editing: Boolean): Boolean = settings.showGetMoreAppsTile && !editing
}

/** Pure layout helpers for the Discover grid (two rows, scrolling sideways). */
object DiscoverGrid {
    const val ROWS = 2
    const val CARD_W_DP = 248
    const val CARD_H_DP = 132
    const val GAP_DP = 16

    /** (column, row) of item [i]. */
    fun cell(i: Int): Pair<Int, Int> = (i / ROWS) to (i % ROWS)

    fun columns(count: Int): Int = (count + ROWS - 1) / ROWS

    /** Letters for the icon-less brand tile: the first letter of up to two words; a trailing '+' is kept. */
    fun initials(label: String): String {
        val t = label.trim()
        if (t.isEmpty()) return "?"
        val plus = t.endsWith("+")
        val words = t.trimEnd('+').split(' ', '-').filter { it.isNotBlank() }
        val letters = words.take(2).mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
            .joinToString("") { it.uppercaseChar().toString() }
        val base = letters.ifEmpty { "?" }
        return if (plus) base.take(1) + "+" else base
    }
}
