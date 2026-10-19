package dev.tvlauncher.helper

/** Pure detector: true only for the second Back-up within the window. */
class DoubleBackDetector(windowMs: Long, private val clock: () -> Long) {
    private val window = clampWindow(windowMs)
    private var last = NONE

    fun onBackUp(): Boolean {
        val now = clock()
        val prev = last
        if (prev != NONE && now - prev in 0..window) {
            last = NONE
            return true
        }
        last = now
        return false
    }

    fun reset() {
        last = NONE
    }

    companion object {
        const val MIN_MS = 300L
        const val MAX_MS = 900L
        const val DEFAULT_MS = 500L
        private const val NONE = Long.MIN_VALUE

        fun clampWindow(ms: Long): Long = ms.coerceIn(MIN_MS, MAX_MS)
    }
}
