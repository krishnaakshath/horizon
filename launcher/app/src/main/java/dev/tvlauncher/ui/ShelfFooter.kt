package dev.tvlauncher.ui

/**
 * Pure positions for the optional end-of-shelf "Get more apps" tile (DiscoverTileView). The
 * footer follows the last app and only exists when the shelf has apps (an empty shelf shows its
 * own empty state, and the top bar keeps focus).
 */
object ShelfFooter {
    fun show(wanted: Boolean, apps: Int): Boolean = wanted && apps > 0

    fun itemCount(apps: Int, footer: Boolean): Int = apps + if (footer && apps > 0) 1 else 0

    fun position(apps: Int, footer: Boolean): Int = if (footer && apps > 0) apps else -1

    fun isFooter(position: Int, apps: Int, footer: Boolean): Boolean = footer && apps > 0 && position == apps
}
