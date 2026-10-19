package dev.tvlauncher.device

enum class EffectsMode {
    LITE,
    FULL
}

data class DeviceProfile(
    val totalRamKb: Long,
    val lowRamFlag: Boolean,
    val widthPx: Int,
    val heightPx: Int,
    val abi: String
) {
    companion object {
        const val LITE_RAM_THRESHOLD_KB = 1_572_864
        const val LITE_APP_OPEN_MS = 250L
        const val FULL_APP_OPEN_MS = 350L
        const val LITE_ICON_CACHE_BYTES = 4 * 1024 * 1024
        const val FULL_ICON_CACHE_BYTES = 16 * 1024 * 1024
    }

    val effectsMode: EffectsMode
        get() = when {
            lowRamFlag || totalRamKb <= LITE_RAM_THRESHOLD_KB -> EffectsMode.LITE
            else -> EffectsMode.FULL
        }

    val appOpenMs: Long
        get() = when (effectsMode) {
            EffectsMode.LITE -> LITE_APP_OPEN_MS
            EffectsMode.FULL -> FULL_APP_OPEN_MS
        }

    val iconCacheBytes: Int
        get() = when (effectsMode) {
            EffectsMode.LITE -> LITE_ICON_CACHE_BYTES
            EffectsMode.FULL -> FULL_ICON_CACHE_BYTES
        }
}
