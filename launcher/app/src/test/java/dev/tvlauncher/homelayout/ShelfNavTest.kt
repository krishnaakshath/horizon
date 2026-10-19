package dev.tvlauncher.homelayout

import dev.tvlauncher.homelayout.NavKey.DOWN
import dev.tvlauncher.homelayout.NavKey.LEFT
import dev.tvlauncher.homelayout.NavKey.PAGE_NEXT
import dev.tvlauncher.homelayout.NavKey.PAGE_PREV
import dev.tvlauncher.homelayout.NavKey.RIGHT
import dev.tvlauncher.homelayout.NavKey.UP
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Adapter slots and launcher-grid D-pad rules for the row, two rows and the grid. */
class ShelfNavTest {
    private fun grid(apps: Int, cols: Int, footer: Boolean = false, pageRows: Int = 3) =
        ShelfGeometry.build(ListKind.GRID, apps, 0, footer, cols, pageRows, cols)

    private fun bands(apps: Int, favorites: Int, footer: Boolean = false, pageColumns: Int = 4) =
        ShelfGeometry.build(ListKind.BANDS, apps, favorites, footer, 2, 2, pageColumns)

    private fun move(g: ShelfGeometry, pos: Int, k: NavKey) = ShelfNav.move(g, pos, k)
    private fun to(p: Int) = NavResult.Move(p)

    @Test fun row_keepsItsSlotsAndPassesEveryKey() {
        val g = ShelfGeometry.build(ListKind.ROW, 5, 2, true, 1, 1, 4)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, ShelfGeometry.FOOTER), g.slots)
        assertEquals(5, g.footerPosition)
        assertEquals(3, g.positionOf(3))
        for (k in NavKey.values()) assertEquals(NavResult.Pass, move(g, 2, k))
    }

    @Test fun empty_hasNoSlots() {
        for (k in ListKind.values()) {
            val g = ShelfGeometry.build(k, 0, 0, true, 4, 3, 4)
            assertEquals(0, g.itemCount)
            assertEquals(-1, g.footerPosition)
            assertEquals(NavResult.Pass, move(g, 0, RIGHT))
        }
    }

    @Test fun grid_leftRightWrapAcrossRows() {
        val g = grid(10, 4) // rows: 0-3, 4-7, 8-9
        assertEquals(to(1), move(g, 0, RIGHT))
        assertEquals(to(4), move(g, 3, RIGHT)) // end of row -> start of the next
        assertEquals(to(3), move(g, 4, LEFT)) // start of row -> end of the previous
        assertEquals(NavResult.Stay, move(g, 0, LEFT))
        assertEquals(NavResult.Stay, move(g, 9, RIGHT))
    }

    @Test fun grid_upDownKeepTheColumn_clampedToAShortRow() {
        val g = grid(10, 4)
        assertEquals(to(6), move(g, 2, DOWN))
        assertEquals(to(9), move(g, 7, DOWN)) // column 3 -> the last tile of the short row
        assertEquals(to(1), move(g, 5, UP))
        assertEquals(NavResult.ExitUp, move(g, 2, UP))
        assertEquals(NavResult.ExitDown, move(g, 8, DOWN))
    }

    @Test fun grid_footerIsTheLastCell() {
        val g = grid(7, 4, footer = true)
        assertEquals(7, g.footerPosition)
        assertEquals(to(7), move(g, 6, RIGHT))
        assertEquals(to(7), move(g, 3, DOWN))
        assertEquals(to(3), move(g, 7, UP))
    }

    @Test fun grid_pagesByRows_andAnchorsPages() {
        val g = grid(40, 4, pageRows = 3) // 10 rows
        assertEquals(to(13), move(g, 1, PAGE_NEXT)) // row 0 -> row 3, same column
        assertEquals(to(1), move(g, 13, PAGE_PREV))
        assertEquals(to(37), move(g, 29, PAGE_NEXT)) // clamped to the last row
        assertEquals(NavResult.Stay, move(g, 1, PAGE_PREV))
        assertEquals(0, ShelfNav.pageAnchor(g, 9)) // rows 0-2
        assertEquals(12, ShelfNav.pageAnchor(g, 13)) // rows 3-5
        assertEquals(36, ShelfNav.pageAnchor(g, 39)) // row 9
        assertEquals(-1, ShelfNav.pageAnchor(bands(6, 2), 0))
    }

    @Test fun bands_favoritesOnTop_interleavedColumnByColumn() {
        // 2 favorites, 5 others: top = [0,1], bottom = [2..6].
        val g = bands(7, 2)
        assertArrayEquals(
            intArrayOf(0, 2, 1, 3, ShelfGeometry.EMPTY, 4, ShelfGeometry.EMPTY, 5, ShelfGeometry.EMPTY, 6),
            g.slots
        )
        assertEquals(2, g.rows.size)
        assertArrayEquals(intArrayOf(0, 2), g.rows[0])
        assertEquals(0, g.positionOf(0))
        assertEquals(3, g.positionOf(3))
        assertEquals(9, g.positionOf(6))
    }

    @Test fun bands_withoutFavorites_splitInHalf() {
        assertEquals(4, ShelfGeometry.topRowCount(7, 0))
        assertEquals(4, ShelfGeometry.topRowCount(7, 7)) // only favorites: split too
        assertEquals(3, ShelfGeometry.topRowCount(7, 3))
        val g = bands(4, 0)
        assertArrayEquals(intArrayOf(0, 2, 1, 3), g.slots)
    }

    @Test fun bands_footerEndsTheBottomRow_andTrailingPaddingIsTrimmed() {
        val g = bands(3, 2, footer = true) // top [0,1], bottom [2, footer]
        assertArrayEquals(intArrayOf(0, 2, 1, ShelfGeometry.FOOTER), g.slots)
        val h = bands(5, 4) // top [0..3], bottom [4]
        assertArrayEquals(intArrayOf(0, 4, 1, ShelfGeometry.EMPTY, 2, ShelfGeometry.EMPTY, 3), h.slots)
    }

    @Test fun bands_navigation() {
        val g = bands(7, 2) // top positions [0,2]; bottom positions [1,3,5,7,9]
        assertEquals(to(2), move(g, 0, RIGHT))
        assertEquals(to(1), move(g, 2, RIGHT)) // end of favorites -> first of the apps row
        assertEquals(to(2), move(g, 1, LEFT))
        assertEquals(to(3), move(g, 2, DOWN))
        assertEquals(to(2), move(g, 9, UP)) // column 4 -> the last favorite
        assertEquals(NavResult.ExitUp, move(g, 0, UP))
        assertEquals(NavResult.ExitDown, move(g, 5, DOWN))
        assertEquals(NavResult.Stay, move(g, 9, RIGHT))
    }

    @Test fun bands_pageSideways() {
        val g = bands(20, 2, pageColumns = 4) // bottom row has 18 apps
        val bottom = g.rows[1]
        assertEquals(to(bottom[4]), move(g, bottom[0], PAGE_NEXT))
        assertEquals(to(bottom[17]), move(g, bottom[15], PAGE_NEXT))
        assertEquals(to(bottom[1]), move(g, bottom[5], PAGE_PREV))
        assertEquals(NavResult.Stay, move(g, bottom[0], PAGE_PREV))
    }

    @Test fun lookups_outOfRange_areSafe() {
        val g = grid(5, 4)
        assertEquals(ShelfGeometry.EMPTY, g.slotAt(-1))
        assertEquals(ShelfGeometry.EMPTY, g.slotAt(99))
        assertEquals(-1, g.positionOf(9))
        assertEquals(NavResult.Pass, move(g, 99, RIGHT))
    }
}
