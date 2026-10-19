package dev.tvlauncher.ui

/** Pure-Kotlin tile constants so they can be asserted on the JVM. */
object TileSpec {
    const val WIDTH_DP = 200
    const val HEIGHT_DP = 112
    const val CORNER_DP = 16
    const val FOCUS_SCALE = 1.08f
    const val FOCUS_MS = 160L
    const val REST_ELEVATION_DP = 2
    const val FOCUS_ELEVATION_DP = 16
    const val REST_ALPHA = 0.85f
    const val FOCUS_ALPHA = 1.0f
    const val PARALLAX_BG_DP = 2
    const val PARALLAX_FG_DP = 6
    /** Spec open question 2: settle on-device in Task 12. */
    const val PARALLAX_IN_LITE = false

    const val LABEL_MAX_LINES = 1
    val LABEL_ELLIPSIZE = Ellipsize.END
    const val LABEL_SP = 14

    enum class Ellipsize { NONE, START, MIDDLE, END }
}
