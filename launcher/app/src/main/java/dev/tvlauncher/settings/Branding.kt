package dev.tvlauncher.settings

import dev.tvlauncher.brand.Brand

/** About-page naming and licensing; the names themselves live in [Brand]. */
object Branding {
    const val NAME = Brand.NAME
    const val DISCLAIMER = "Not affiliated with Google, Apple, Netflix or any app shown."
    const val LICENSE = "MIT"
    /** Copyright holder shown on the About page and substituted into the bundled MIT text. */
    const val COPYRIGHT_HOLDER = Brand.COPYRIGHT_HOLDER
    const val COPYRIGHT_YEAR = "2026"

    /** Placeholder in res/raw/license_mit.txt replaced with [COPYRIGHT_HOLDER] at display time. */
    const val HOLDER_TOKEN = "{{COPYRIGHT_HOLDER}}"
}
