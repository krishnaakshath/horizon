package dev.tvlauncher.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceProfileTest {
    @Test
    fun lowRamFlag_true_isLite() {
        val profile = DeviceProfile(
            totalRamKb = 2_097_152, // 2 GB
            lowRamFlag = true,
            widthPx = 1280,
            heightPx = 720,
            abi = "armeabi-v7a"
        )
        assertEquals(EffectsMode.LITE, profile.effectsMode)
    }

    @Test
    fun ram_1_5GB_isLite() {
        val profile = DeviceProfile(
            totalRamKb = 1_572_864,
            lowRamFlag = false,
            widthPx = 1280,
            heightPx = 720,
            abi = "armeabi-v7a"
        )
        assertEquals(EffectsMode.LITE, profile.effectsMode)
    }

    @Test
    fun ram_1_5GB_plus_1kB_notLowRamFlag_isFull() {
        val profile = DeviceProfile(
            totalRamKb = 1_572_865,
            lowRamFlag = false,
            widthPx = 1280,
            heightPx = 720,
            abi = "armeabi-v7a"
        )
        assertEquals(EffectsMode.FULL, profile.effectsMode)
    }

    @Test
    fun ram_3GB_notLowRam_isFull() {
        val profile = DeviceProfile(
            totalRamKb = 3_145_728, // 3 GB
            lowRamFlag = false,
            widthPx = 1920,
            heightPx = 1080,
            abi = "arm64-v8a"
        )
        assertEquals(EffectsMode.FULL, profile.effectsMode)
    }

    @Test
    fun lite_appOpenMs_is_250() {
        val profile = DeviceProfile(
            totalRamKb = 1_572_864,
            lowRamFlag = false,
            widthPx = 1280,
            heightPx = 720,
            abi = "armeabi-v7a"
        )
        assertEquals(250L, profile.appOpenMs)
    }

    @Test
    fun full_appOpenMs_is_350() {
        val profile = DeviceProfile(
            totalRamKb = 3_145_728,
            lowRamFlag = false,
            widthPx = 1920,
            heightPx = 1080,
            abi = "arm64-v8a"
        )
        assertEquals(350L, profile.appOpenMs)
    }

    @Test
    fun lite_iconCacheBytes_isSmallerThan_full() {
        val liteProfile = DeviceProfile(
            totalRamKb = 1_572_864,
            lowRamFlag = false,
            widthPx = 1280,
            heightPx = 720,
            abi = "armeabi-v7a"
        )
        val fullProfile = DeviceProfile(
            totalRamKb = 3_145_728,
            lowRamFlag = false,
            widthPx = 1920,
            heightPx = 1080,
            abi = "arm64-v8a"
        )
        assert(liteProfile.iconCacheBytes < fullProfile.iconCacheBytes)
    }
}
