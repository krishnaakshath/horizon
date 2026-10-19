package dev.tvlauncher.type

enum class FontWeight { LIGHT, REGULAR, MEDIUM, SEMIBOLD, BOLD }

/**
 * Pure mapping from a weight to its bundled asset and the system fallback used when the asset
 * cannot be loaded. No Android classes are touched so it is unit-testable on the JVM.
 * fallbackStyle uses the android.graphics.Typeface constants (0 = NORMAL, 1 = BOLD).
 */
object FontPlan {
    data class Entry(
        val weight: FontWeight,
        val assetName: String,
        val fallbackFamily: String,
        val fallbackStyle: Int,
    )

    private val entries: Map<FontWeight, Entry> = mapOf(
        FontWeight.LIGHT to Entry(FontWeight.LIGHT, "fonts/ui_light.ttf", "sans-serif-light", 0),
        FontWeight.REGULAR to Entry(FontWeight.REGULAR, "fonts/ui_regular.ttf", "sans-serif", 0),
        FontWeight.MEDIUM to Entry(FontWeight.MEDIUM, "fonts/ui_medium.ttf", "sans-serif-medium", 0),
        FontWeight.SEMIBOLD to Entry(FontWeight.SEMIBOLD, "fonts/ui_semibold.ttf", "sans-serif-medium", 1),
        FontWeight.BOLD to Entry(FontWeight.BOLD, "fonts/ui_bold.ttf", "sans-serif", 1),
    )

    fun entry(weight: FontWeight): Entry = entries.getValue(weight)

    /** Asset paths (relative to assets/) of every bundled font file. */
    fun assetNames(): List<String> = FontWeight.values().map { entries.getValue(it).assetName }

    fun fromName(name: String?): FontWeight {
        val n = name?.trim()?.uppercase() ?: return FontWeight.REGULAR
        return FontWeight.values().firstOrNull { it.name == n } ?: FontWeight.REGULAR
    }

    fun fromCssWeight(w: Int): FontWeight = when {
        w <= 0 -> FontWeight.REGULAR
        w <= 349 -> FontWeight.LIGHT
        w <= 449 -> FontWeight.REGULAR
        w <= 549 -> FontWeight.MEDIUM
        w <= 649 -> FontWeight.SEMIBOLD
        else -> FontWeight.BOLD
    }
}
