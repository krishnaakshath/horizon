package dev.tvlauncher.type

/**
 * Manrope's native line height (hhea ascent 2132 + descent 600, lineGap 0, upm 2000, identical in all five
 * bundled instances; OS/2 typo metrics are the same and USE_TYPO_METRICS is set) = 2732/2000 = 1.366 em.
 * Android's lineSpacingMultiplier scales that, so multiplier = effective / natural.
 */
object LineHeight {
    const val MANROPE_NATURAL_EM = 1.366f

    fun multiplierFor(naturalEm: Float, effectiveEm: Float): Float {
        require(naturalEm > 0f && effectiveEm > 0f)
        return effectiveEm / naturalEm
    }
}
