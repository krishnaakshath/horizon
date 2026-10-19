package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandColorsTest {
    private fun argb(c: Long) = c.toInt()
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun knownApps_returnExactBrandColor() {
        assertEquals(argb(0xFFE50914), BrandColors.forApp("com.netflix.ninja", null))
        assertEquals(argb(0xFFFF0000), BrandColors.forApp("com.google.android.youtube.tv", null))
        assertEquals(argb(0xFFFF0000), BrandColors.forApp("com.google.android.youtube.tvmusic", null))
        assertEquals(argb(0xFF00A8E1), BrandColors.forApp("com.amazon.amazonvideo.livingroom", null))
        assertEquals(argb(0xFF113CCF), BrandColors.forApp("com.disney.disneyplus", null))
        assertEquals(argb(0xFF0C1B5E), BrandColors.forApp("in.startv.hotstar", null))
        assertEquals(argb(0xFF1DB954), BrandColors.forApp("com.spotify.tv.android", null))
        assertEquals(argb(0xFFE5A00D), BrandColors.forApp("com.plexapp.android", null))
        assertEquals(argb(0xFF01875F), BrandColors.forApp("com.android.vending", null))
        assertEquals(argb(0xFF3A3F4B), BrandColors.forApp("com.android.tv.settings", null))
        assertEquals(BrandColors.APPLE_TV, BrandColors.forApp("com.apple.atve.androidtv.appletv", null))
    }

    @Test fun knownApp_ignoresArtPixels() {
        val green = IntArray(64) { argb(0xFF00C853) }
        assertEquals(argb(0xFFE50914), BrandColors.forApp("com.netflix.ninja", green))
    }

    @Test fun unknownApp_usesDominantColorOfArt() {
        // Mostly blue with a few orange pixels and lots of white/black noise.
        val px = IntArray(400) { i ->
            when {
                i < 200 -> argb(0xFF1E5BD8)
                i < 230 -> argb(0xFFFF8800)
                i < 330 -> argb(0xFFFFFFFF)
                else -> argb(0xFF000000)
            }
        }
        val c = BrandColors.forApp("com.example.unknown", px)
        assertEquals(argb(0xFF1E5BD8), c)
    }

    @Test fun dominantColor_ignoresNearWhiteAndNearBlack() {
        val px = IntArray(1000) { i ->
            when {
                i < 450 -> argb(0xFFFAFAFA)
                i < 900 -> argb(0xFF050505)
                else -> argb(0xFF2E7D32)
            }
        }
        assertEquals(argb(0xFF2E7D32), BrandColors.dominantColor(px))
    }

    @Test fun dominantColor_ignoresTransparentPixels() {
        val px = IntArray(100) { i -> if (i < 90) 0x00FF0000 else argb(0xFF1565C0) }
        assertEquals(argb(0xFF1565C0), BrandColors.dominantColor(px))
    }

    @Test fun dominantColor_allGray_returnsNeutralFallback() {
        val px = IntArray(256) { i -> val v = i; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        assertEquals(BrandColors.NEUTRAL, BrandColors.dominantColor(px))
    }

    @Test fun dominantColor_largeInput_isDownsampled_andStillCorrect() {
        val px = IntArray(1920 * 1080) { i -> if (i % 7 < 3) argb(0xFFFFFFFF) else argb(0xFFC62828) }
        assertEquals(argb(0xFFC62828), BrandColors.dominantColor(px))
    }

    @Test fun dominantColor_empty_returnsNeutral() {
        assertEquals(BrandColors.NEUTRAL, BrandColors.dominantColor(IntArray(0)))
    }

    @Test fun unknownApp_noArt_returnsNeutral() {
        assertEquals(BrandColors.NEUTRAL, BrandColors.forApp("com.example.unknown", null))
    }

    @Test fun onColorTextIsLight_netflixRed_isLight() {
        assertTrue(BrandColors.onColorTextIsLight(argb(0xFFE50914)))
    }

    @Test fun onColorTextIsLight_lightYellow_isDark() {
        assertFalse(BrandColors.onColorTextIsLight(argb(0xFFFFF59D)))
    }

    @Test fun onColorTextIsLight_base_isLight() {
        assertTrue(BrandColors.onColorTextIsLight(argb(0xFF0B0D12)))
    }

    @Test fun cache_computesOncePerPackage() {
        val cache = BrandColors.Cache()
        var calls = 0
        val px = IntArray(16) { argb(0xFF1565C0) }
        val a = cache.get("com.example.a") { calls++; px }
        val b = cache.get("com.example.a") { calls++; px }
        assertEquals(a, b)
        assertEquals(1, calls)
    }

    @Test fun backdropAccent_liftsVeryDarkBrands() {
        val lifted = BrandColors.backdropAccent(argb(0xFF1C1C1E))
        assertNotEquals(argb(0xFF1C1C1E), lifted)
        assertTrue(r(lifted) + g(lifted) + b(lifted) > 3 * 0x50)
        // A bright brand is left alone.
        assertEquals(argb(0xFFE50914), BrandColors.backdropAccent(argb(0xFFE50914)))
    }

    @Test fun blend_endpoints() {
        assertEquals(argb(0xFF000000), BrandColors.blend(argb(0xFF000000), argb(0xFFFFFFFF), 0f))
        assertEquals(argb(0xFFFFFFFF), BrandColors.blend(argb(0xFF000000), argb(0xFFFFFFFF), 1f))
    }
}
