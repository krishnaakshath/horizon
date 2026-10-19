package dev.tvlauncher.home

import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AnimationLevel
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.ClockFormat
import dev.tvlauncher.settings.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAppearanceTest {
    private val liteTv = DeviceProfile(1_000_000, true, 1280, 720, "armeabi-v7a")
    private val fullTv = DeviceProfile(4_000_000, false, 1920, 1080, "arm64-v8a")

    @Test
    fun tileScale_perSize() {
        assertEquals(0.85f, HomeAppearance.tileScale(TileSize.SMALL), 0f)
        assertEquals(1.0f, HomeAppearance.tileScale(TileSize.MEDIUM), 0f)
        assertEquals(1.2f, HomeAppearance.tileScale(TileSize.LARGE), 0f)
    }

    @Test
    fun accent_auto_usesBrandColor() {
        val a = AppearanceSettings(accentMode = AccentMode.AUTO_BRAND, fixedAccent = 0xFF00FF00.toInt())
        assertEquals(0xFF123456.toInt(), HomeAppearance.accent(a) { 0xFF123456.toInt() })
    }

    @Test
    fun accent_fixed_ignoresBrand_andNeverSamples() {
        val a = AppearanceSettings(accentMode = AccentMode.FIXED, fixedAccent = 0xFF00FF00.toInt())
        var sampled = false
        assertEquals(0xFF00FF00.toInt(), HomeAppearance.accent(a) { sampled = true; 0xFF123456.toInt() })
        assertFalse(sampled)
    }

    @Test
    fun profile_off_hasNoAnimations() {
        val p = HomeAppearance.profile(fullTv, AppearanceSettings(animationLevel = AnimationLevel.OFF), 1f)
        assertNull(p.motion)
        assertFalse(p.animate)
        assertFalse(p.full)
        assertEquals(EffectsMode.LITE, p.effectsMode) // existing `effectsMode == FULL` call sites stay off
        assertEquals(0L, HomeAppearance.backdropMs(p.motion))
        assertFalse(HomeAppearance.showSplash(p.motion))
    }

    @Test
    fun profile_auto_followsDevice() {
        val lite = HomeAppearance.profile(liteTv, AppearanceSettings(), 1f)
        assertEquals(EffectsMode.LITE, lite.motion)
        assertTrue(lite.animate)
        assertEquals(250L, HomeAppearance.backdropMs(lite.motion))
        val full = HomeAppearance.profile(fullTv, AppearanceSettings(), 1f)
        assertTrue(full.full)
        assertEquals(350L, HomeAppearance.backdropMs(full.motion))
        assertTrue(HomeAppearance.showSplash(full.motion))
    }

    @Test
    fun profile_systemScaleZero_disablesAnimations() {
        val p = HomeAppearance.profile(fullTv, AppearanceSettings(animationLevel = AnimationLevel.FULL), 0f)
        assertNull(p.motion)
    }

    @Test
    fun profile_carriesTileScale_andDevice() {
        val p = HomeAppearance.profile(liteTv, AppearanceSettings(rowTileSize = TileSize.LARGE), 1f)
        assertEquals(1.2f, p.tileScale, 0f)
        assertEquals(liteTv, p.device)
        assertEquals(liteTv.appOpenMs, p.appOpenMs)
    }

    @Test
    fun change_none_live_rebuild() {
        val a = AppearanceSettings()
        assertEquals(HomeAppearance.Change.NONE, HomeAppearance.change(a, EffectsMode.LITE, a, EffectsMode.LITE))
        for (b in listOf(a.copy(clockFormat = ClockFormat.H12), a.copy(showDate = false), a.copy(showStatusIcons = false),
            a.copy(accentMode = AccentMode.FIXED), a.copy(fixedAccent = 0xFF000001.toInt()))) {
            assertEquals(b.toString(), HomeAppearance.Change.LIVE, HomeAppearance.change(a, EffectsMode.LITE, b, EffectsMode.LITE))
        }
        assertEquals(HomeAppearance.Change.REBUILD,
            HomeAppearance.change(a, EffectsMode.LITE, a.copy(rowTileSize = TileSize.LARGE), EffectsMode.LITE))
        assertEquals(HomeAppearance.Change.REBUILD, HomeAppearance.change(a, EffectsMode.LITE, a, null))
        // A level change that resolves to the same motion needs no rebuild.
        assertEquals(HomeAppearance.Change.LIVE,
            HomeAppearance.change(a, EffectsMode.LITE, a.copy(animationLevel = AnimationLevel.LITE), EffectsMode.LITE))
    }
}
