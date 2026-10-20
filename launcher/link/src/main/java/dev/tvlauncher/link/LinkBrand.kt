package dev.tvlauncher.link

/** Pure brand constants (ARGB ints); no Android dependencies. */
object LinkBrand {
    const val APP_NAME = "H02 Link" // mirrors R.string.app_name and Brand.LINK in the launcher
    const val BROWSER = "H02 Browser"
    const val STORE = "H02 Store"
    const val HELPER = "H02 Helper"

    const val BG = 0xFF0B0D12.toInt()
    const val SURFACE = 0xFF151922.toInt()
    const val SURFACE_FOCUS = 0xFF1E2430.toInt()
    const val TEXT = 0xFFF5F7FA.toInt()
    const val TEXT_DIM = 0xFF9AA4B5.toInt()
    const val ACCENT = 0xFF4FC3F7.toInt()
    const val ERROR = 0xFFFF8A80.toInt()
}
