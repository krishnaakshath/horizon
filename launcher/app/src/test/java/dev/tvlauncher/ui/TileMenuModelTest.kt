package dev.tvlauncher.ui

import dev.tvlauncher.R
import dev.tvlauncher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class TileMenuModelTest {
    private val cur = listOf("a", "b", "c")

    @Test fun middleTile_hasEveryEntry_uninstallLast() {
        val keys = TileMenuModel.entries(LauncherSettings(), "b", cur).map { it.key }
        assertEquals(listOf("favorite", "left", "right", "hide", "info", "uninstall"), keys)
    }

    @Test fun edges_dropImpossibleMoves() {
        assertEquals(listOf("favorite", "right", "hide", "info", "uninstall"),
            TileMenuModel.entries(LauncherSettings(), "a", cur).map { it.key })
        assertEquals(listOf("favorite", "left", "hide", "info", "uninstall"),
            TileMenuModel.entries(LauncherSettings(), "c", cur).map { it.key })
    }

    @Test fun favoriteLabelAndIconFollowState() {
        val off = TileMenuModel.entries(LauncherSettings(), "b", cur).first()
        assertEquals("Add to favorites", off.label)
        assertEquals(R.drawable.ic_star_border, off.icon)
        val on = TileMenuModel.entries(LauncherSettings(favorites = listOf("b")), "b", cur).first()
        assertEquals("Remove from favorites", on.label)
        assertEquals(R.drawable.ic_star, on.icon)
    }

    @Test fun everyRowHasAnIcon_andFitsTheVisibleLimit() {
        val rows = TileMenuModel.entries(LauncherSettings(), "b", cur)
        assertEquals(0, rows.count { it.icon == null })
        assert(rows.size <= dev.tvlauncher.panel.PanelLimits.MAX_VISIBLE_ROWS)
        assertEquals(listOf("unhide"), TileMenuModel.dockEntries().map { it.key })
    }
}
