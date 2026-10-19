package dev.tvlauncher.type

/**
 * Inter's native line height (hhea ascent 1984 + descent 494, lineGap 0, upm 2048, identical in all five
 * bundled instances; OS/2 typo metrics are the same and USE_TYPO_METRICS is set) = 2478/2048 = 1.21 em.
 * Android's lineSpacingMultiplier scales that, so multiplier = effective / natural.
 */
object LineHeight {
    const val INTER_NATURAL_EM = 1.21f

    fun multiplierFor(naturalEm: Float, effectiveEm: Float): Float {
        require(naturalEm > 0f && effectiveEm > 0f)
        return effectiveEm / naturalEm
    }
}
