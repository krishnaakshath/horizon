package dev.tvlauncher.homelayout

import dev.tvlauncher.ui.QuickStrip
import dev.tvlauncher.ui.TileSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tile counts per screen, safe-area fit and no overlaps for every style, tile size and screen. */
class LayoutStyleTest {
    private val screens = listOf(ScreenDp.TV, ScreenDp.MDPI_720, ScreenDp.XHDPI_4K)
    private val scales = listOf(0.85f, 1f, 1.2f) // HomeAppearance.tileScale: S, M, L

    private fun plan(style: HomeStyle, scale: Float = 1f, screen: ScreenDp = ScreenDp.TV, strip: Boolean = true) =
        LayoutStyle.plan(HomeLayoutSettings(style = style, quickStrip = strip), scale, screen)

    private fun all() = sequence {
        for (st in HomeStyle.values()) for (sc in scales) for (scr in screens) for (strip in listOf(true, false))
            yield(Triple(plan(st, sc, scr, strip), scr, "$st x$sc ${scr.widthDp}x${scr.heightDp} strip=$strip"))
    }

    @Test fun stripRow_matchesTheQuickStrip() {
        assertEquals(QuickStrip.ROW_DP, LayoutStyle.STRIP_ROW_DP)
    }

    @Test fun singleRow_isTodaysShelf() {
        val p = plan(HomeStyle.SINGLE_ROW)
        assertEquals(ListKind.ROW, p.kind)
        assertEquals(TileSpec.dims(1f), p.dims)
        assertEquals(1, p.visibleRows)
        assertEquals(TileSpec.VIEW_HEIGHT_DP + 2 * TileSpec.PAD_DP, p.listHeightDp)
        assertEquals(HeroMode.TEXT, p.hero)
        assertTrue(p.quickStrip)
        assertNull(p.listWidthDp)
    }

    @Test fun tileCounts_on960x540dp_theUsualTv() {
        assertEquals(4, plan(HomeStyle.SINGLE_ROW).columns)
        plan(HomeStyle.TWO_ROWS).let { assertEquals(2, it.visibleRows); assertEquals(5, it.columns); assertEquals(2, it.lanes) }
        plan(HomeStyle.GRID).let { assertEquals(3, it.visibleRows); assertEquals(5, it.columns); assertEquals(5, it.lanes) }
        assertEquals(4, plan(HomeStyle.BIG_HERO).columns)
        assertEquals(4, plan(HomeStyle.MINIMAL).columns)
    }

    @Test fun tileCounts_growWithTheScreenInDp() {
        for (st in HomeStyle.values()) {
            val tv = plan(st, screen = ScreenDp.TV)
            val p720 = plan(st, screen = ScreenDp.MDPI_720)
            val p4k = plan(st, screen = ScreenDp.XHDPI_4K)
            assertTrue("$st", tv.columns * tv.visibleRows <= p720.columns * p720.visibleRows)
            assertTrue("$st", p720.columns * p720.visibleRows <= p4k.columns * p4k.visibleRows)
        }
        assertEquals(4, plan(HomeStyle.GRID, screen = ScreenDp.MDPI_720).visibleRows)
        assertEquals(4, plan(HomeStyle.GRID, screen = ScreenDp.XHDPI_4K).visibleRows)
        assertEquals(8, plan(HomeStyle.SINGLE_ROW, screen = ScreenDp.XHDPI_4K).columns)
    }

    @Test fun grid_hasThreeOrFourRows_everywhere_andNoStripRow() {
        for (sc in scales) for (scr in screens) for (strip in listOf(true, false)) {
            val p = plan(HomeStyle.GRID, sc, scr, strip)
            assertTrue("${p.visibleRows} rows", p.visibleRows in LayoutStyle.GRID_MIN_ROWS..LayoutStyle.GRID_MAX_ROWS)
            assertEquals(ListKind.GRID, p.kind)
            assertEquals(0, p.stripRowDp)
            assertFalse(p.quickStrip)
            assertEquals(p.columns, p.lanes)
        }
    }

    @Test fun grid_onABigScreen_growsTowardTheUsersTileSize() {
        val p = plan(HomeStyle.GRID, 1f, ScreenDp.XHDPI_4K)
        assertTrue("scale ${p.tileScale}", p.tileScale > LayoutStyle.baseScale(HomeStyle.GRID))
        assertTrue(p.tileScale <= 1f)
    }

    @Test fun tileSize_affectsTheRow() {
        for (st in listOf(HomeStyle.SINGLE_ROW, HomeStyle.BIG_HERO, HomeStyle.MINIMAL)) for (scr in screens) {
            val s = plan(st, 0.85f, scr).dims.widthDp
            val m = plan(st, 1f, scr).dims.widthDp
            val l = plan(st, 1.2f, scr).dims.widthDp
            assertTrue("$st $s $m $l", s < m && m < l)
        }
        for (st in HomeStyle.values()) for (scr in screens) {
            assertTrue("$st", plan(st, 0.85f, scr).dims.widthDp <= plan(st, 1.2f, scr).dims.widthDp)
        }
    }

    @Test fun compactStyles_useSmallerTilesThanTheSingleRow() {
        for (sc in scales) for (scr in screens) {
            val row = plan(HomeStyle.SINGLE_ROW, sc, scr).dims.widthDp
            for (st in listOf(HomeStyle.TWO_ROWS, HomeStyle.BIG_HERO)) assertTrue("$st", plan(st, sc, scr).dims.widthDp < row)
            assertTrue(plan(HomeStyle.GRID, sc, scr).dims.widthDp <= row)
        }
    }

    @Test fun everyPlan_fitsTheSafeArea() {
        for ((p, scr, what) in all()) {
            val used = LayoutStyle.TOP_BAR_DP + p.heroHeightDp + p.listHeightDp + p.stripRowDp
            assertEquals(what, scr.heightDp - 2 * LayoutStyle.SAFE_V_DP, used)
            assertTrue(what, p.heroHeightDp >= 0)
            when (p.hero) {
                HeroMode.TEXT -> assertTrue(what, p.heroHeightDp >= LayoutStyle.HERO_MIN_DP)
                HeroMode.ART -> assertTrue(what, p.artHeightDp >= LayoutStyle.ART_MIN_DP && p.artHeightDp + LayoutStyle.ART_CHROME_DP <= p.heroHeightDp)
                HeroMode.NONE -> Unit
            }
            // The last whole card ends inside the right safe margin.
            val lastCardRight = LayoutStyle.SAFE_H_DP + (p.columns - 1) * p.pitchDp + p.dims.widthDp
            assertTrue(what, lastCardRight <= scr.widthDp - LayoutStyle.SAFE_H_DP)
            p.listWidthDp?.let { assertTrue(what, it <= scr.widthDp) }
            assertTrue(what, p.columns >= 1 && p.visibleRows >= 1 && p.tileScale >= LayoutStyle.MIN_SCALE)
        }
    }

    @Test fun bigHeroArt_isSixteenByNine_andLeavesRoomForTheTitle() {
        for (sc in scales) for (scr in screens) {
            val p = plan(HomeStyle.BIG_HERO, sc, scr)
            assertEquals(HeroMode.ART, p.hero)
            assertTrue(p.artWidthDp <= (scr.widthDp - 2 * LayoutStyle.SAFE_H_DP) * 45 / 100)
            assertEquals(p.artWidthDp * 9 / 16.0, p.artHeightDp.toDouble(), 1.0)
        }
    }

    @Test fun minimal_andGrid_haveNoHeroText() {
        for (st in listOf(HomeStyle.MINIMAL, HomeStyle.GRID)) {
            val p = LayoutStyle.plan(HomeLayoutSettings(style = st, heroSubtitle = true), 1f, ScreenDp.TV)
            assertEquals(HeroMode.NONE, p.hero)
            assertFalse(p.heroSubtitle)
        }
        assertFalse(LayoutStyle.plan(HomeLayoutSettings(heroSubtitle = false), 1f, ScreenDp.TV).heroSubtitle)
    }

    @Test fun gridWidth_makesEverySpanOneTilePitch() {
        for (sc in scales) for (scr in screens) {
            val p = plan(HomeStyle.GRID, sc, scr)
            val inner = p.listWidthDp!! - 2 * (LayoutStyle.SAFE_H_DP - TileSpec.PAD_DP)
            assertEquals(p.columns * p.pitchDp, inner)
        }
    }

    @Test fun preview_blocksStayInTheSafeArea_andNeverOverlap() {
        for ((p, scr, what) in all()) {
            val blocks = PreviewSchematic.blocks(p, scr)
            val l = LayoutStyle.SAFE_H_DP / scr.widthDp.toFloat() - 1e-4f
            val t = LayoutStyle.SAFE_V_DP / scr.heightDp.toFloat() - 1e-4f
            for (b in blocks) {
                assertTrue("$what $b", b.left >= l && b.right <= 1 - l && b.top >= t && b.bottom <= 1 - t)
                assertTrue("$what $b", b.right > b.left && b.bottom > b.top)
            }
            for (i in blocks.indices) for (j in i + 1 until blocks.size) {
                assertFalse("$what ${blocks[i]} / ${blocks[j]}", blocks[i].intersects(blocks[j]))
            }
            val tiles = blocks.count { it.part == PreviewSchematic.Part.TILE || it.part == PreviewSchematic.Part.TILE_FOCUSED }
            assertEquals(what, p.columns * p.visibleRows, tiles)
            assertEquals(what, p.stripRowDp > 0, blocks.any { it.part == PreviewSchematic.Part.STRIP })
            assertEquals(what, p.hero == HeroMode.ART, blocks.any { it.part == PreviewSchematic.Part.HERO_ART })
        }
    }

    @Test fun screenDp_fromPixels() {
        assertEquals(ScreenDp(960, 540), ScreenDp.of(1920, 1080, 2f))
        assertEquals(ScreenDp(960, 540), ScreenDp.of(3840, 2160, 4f))
        assertEquals(ScreenDp(1280, 720), ScreenDp.of(1280, 720, 1f))
    }
}
