package dev.tvlauncher.browser

/**
 * The browser's own identity ("Orbit"): an open outer orbit, a counter-orbit inside it and a
 * satellite dot in the gap, in a blue -> violet -> cyan gradient. Geometry is on a 48x48 grid; the
 * wordmark is stroked geometric letters on a 20-unit cap height. The XML assets (brand_mark,
 * banner_browser, launcher icon) use these exact path strings (checked by a unit test), so a
 * future app family (store, weather) can reuse the same style language.
 */
object BrowserBrand {
    const val BLUE = 0xFF5B8CFF.toInt()
    const val VIOLET = 0xFF8B5CF6.toInt()
    const val CYAN = 0xFF22D3EE.toInt()
    val GRADIENT = intArrayOf(BLUE, VIOLET, CYAN)
    const val INK = 0xFFF5F7FA.toInt()
    const val NIGHT = 0xFF0B0D12.toInt()

    /** Stroke widths on the 48 grid. */
    const val OUTER_STROKE = 5f
    const val INNER_STROKE = 4f
    const val WORD_STROKE = 3f

    const val ORBIT_OUTER = "M37.86,16 A16,16 0 1,1 16,10.14"
    const val ORBIT_INNER = "M20,30.93 A8,8 0 0,1 28,17.07"
    const val SATELLITE = "M28.14,8.55 m-3.5,0 a3.5,3.5 0 1,0 7,0 a3.5,3.5 0 1,0 -7,0"
    val MARK_PATHS = listOf(ORBIT_OUTER, ORBIT_INNER, SATELLITE)

    /** Letter -> (x offset, path) for the "Browser" wordmark; total width [WORDMARK_WIDTH]. */
    val WORDMARK: List<Triple<Char, Float, String>> = listOf(
        Triple('B', 0f, "M1.5,1.5 V18.5 M1.5,1.5 H6 A4.25,4.25 0 0,1 6,10 H1.5 M1.5,10 H7 A4.25,4.25 0 0,1 7,18.5 H1.5"),
        Triple('r', 16f, "M1.5,18.5 V6.5 M1.5,11 A5,5 0 0,1 6.5,6.5 H8"),
        Triple('o', 28f, "M1.5,12.5 A6,6 0 1,0 13.5,12.5 A6,6 0 1,0 1.5,12.5"),
        Triple('w', 46f, "M1.5,6.5 L4.75,18.5 L8,9.5 L11.25,18.5 L14.5,6.5"),
        Triple('s', 65f, "M10,8 C8.5,6 1.5,6 1.5,9.5 C1.5,13 10.5,12 10.5,15.5 C10.5,19.5 3,19.5 1.5,17"),
        Triple('e', 80f, "M1.5,12.5 H13.5 A6,6 0 1,0 11.7,16.8"),
        Triple('r', 98f, "M1.5,18.5 V6.5 M1.5,11 A5,5 0 0,1 6.5,6.5 H8")
    )
    const val WORDMARK_WIDTH = 108f
    val WORDMARK_PATHS: Map<String, String> = WORDMARK.mapIndexed { i, t -> "${t.first}$i" to t.third }.toMap()
}
