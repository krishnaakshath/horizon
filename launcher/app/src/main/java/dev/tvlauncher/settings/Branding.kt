package dev.tvlauncher.settings

/** Product naming in one place so the launcher can be renamed with a single edit. */
object Branding {
    const val NAME = "TV Launcher"
    const val DISCLAIMER = "Not affiliated with Google, Apple, Netflix or any app shown."
    const val LICENSE = "MIT"
    /** Copyright holder shown on the About page and substituted into the bundled MIT text. */
    const val COPYRIGHT_HOLDER = "The TV Launcher contributors"
    const val COPYRIGHT_YEAR = "2026"

    /** Placeholder in res/raw/license_mit.txt replaced with [COPYRIGHT_HOLDER] at display time. */
    const val HOLDER_TOKEN = "{{COPYRIGHT_HOLDER}}"
}
