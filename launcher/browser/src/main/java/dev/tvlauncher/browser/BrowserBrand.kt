package dev.tvlauncher.browser

/**
 * M02 Browser's identity, in the M02 family look: the M02 mark (brand font outlines as vector
 * paths) on a glass card or disc over a horizon sunrise, with a violet sun. The XML assets
 * (brand_mark, brand_mark_reveal, brand_wordmark, banner_browser, launcher icons) are written by
 * fonts/companion_art.py; BrowserBrandTest checks they keep that look. The custom D-pad cursor
 * (ic_cursor) is the browser's own and is not part of the mark.
 */
object BrowserBrand {
    const val NAME = "M02 Browser"
    /** The sun of the browser's sunrise (the store's is teal, the launcher's blue). */
    const val ACCENT = 0xFF8B5CF6.toInt()

    /** Drawables that make up the identity, all generated vector art. */
    val ASSETS = listOf(
        "drawable/brand_mark.xml", "drawable/brand_mark_anim_base.xml", "drawable/brand_wordmark.xml",
        "drawable/banner_browser.xml", "drawable/ic_launcher_fg.xml", "drawable/ic_launcher_bg.xml", "mipmap-anydpi/ic_launcher.xml"
    )
}
