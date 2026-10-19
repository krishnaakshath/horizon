package dev.tvlauncher.ui

import dev.tvlauncher.apps.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileArtTest {
    private val app = AppEntry("com.example.app", "Example", "com.example.app.Main")

    private class FakeSource(
        val banner: String? = null,
        val layers: Pair<String, String>? = null,
        val legacy: String? = null
    ) : ArtSource<String> {
        override fun banner(pkg: String) = banner
        override fun adaptiveLayers(pkg: String) = layers
        override fun legacyIcon(pkg: String) = legacy
    }

    @Test fun banner_beatsAdaptive() {
        val art = TileArtResolver.resolve(FakeSource("b", "bg" to "fg", "l"), app)
        assertEquals(TileArt.Banner("b"), art)
    }

    @Test fun adaptive_beatsLegacy() {
        val art = TileArtResolver.resolve(FakeSource(null, "bg" to "fg", "l"), app)
        assertEquals(TileArt.Adaptive("bg", "fg"), art)
    }

    @Test fun legacy_usedWhenNothingBetter() {
        assertEquals(TileArt.Legacy("l"), TileArtResolver.resolve(FakeSource(legacy = "l"), app))
    }

    @Test fun noArt_returnsGeneratedLetterTile() {
        val art = TileArtResolver.resolve(FakeSource(), app)
        assertTrue(art is TileArt.Letter)
        art as TileArt.Letter
        assertEquals('E', art.letter)
        assertEquals(art.color, TileArtResolver.resolve(FakeSource(), app).let { (it as TileArt.Letter).color })
    }

    @Test fun longLabel_singleLineEllipsized() {
        assertEquals(1, TileSpec.LABEL_MAX_LINES)
        assertEquals(TileSpec.Ellipsize.END, TileSpec.LABEL_ELLIPSIZE)
    }

    @Test fun letterTile_emptyLabel_usesQuestionMark() {
        val t = TileArtResolver.letterTile(AppEntry("com.x", "  ", "c"))
        assertEquals('?', t.letter)
    }

    @Test fun letterTile_colorStable_forSamePackage_differsAcrossPackages() {
        val a1 = TileArtResolver.letterTile(AppEntry("a", "A", "c"))
        val a2 = TileArtResolver.letterTile(AppEntry("a", "Z", "c"))
        val b = TileArtResolver.letterTile(AppEntry("b", "A", "c"))
        assertEquals(a1.color, a2.color)
        assertTrue(a1.color != b.color)
    }
}
