package dev.tvlauncher.brand

/**
 * The product's names, in one place. The product is [NAME] (About page, notices, docs); the screen
 * shows the short mark [SHORT] (app labels, splash, setup, Safe Mode). Every module's
 * `strings.xml` app_name mirrors [SHORT] (BrandStringsTest pins them), so launcher tiles and the
 * system's app lists follow this file. Package names (`dev.tvlauncher.*`) are internal ids and
 * deliberately not part of the brand: renaming them would reset every TV's saved state.
 */
object Brand {
    const val NAME = "Horizon"
    const val SHORT = "H02"
    /** The letter-spaced caption under the splash mark. */
    const val CAPTION = "HORIZON"
    /** One line under the name on the About page. */
    const val TAGLINE = "A calm, fast home for your TV."
    const val COPYRIGHT_HOLDER = "The $NAME contributors"

    // Companion apps: "<short> <role>", the same text as each module's app_name.
    const val BROWSER = "$SHORT Browser"
    const val STORE = "$SHORT Store"
    const val WEATHER = "$SHORT Weather"
    const val HELPER = "$SHORT Helper"
    /** Phone remote, smart home and update check (the launcher itself stays offline). */
    const val LINK = "$SHORT Link"
    /** Screensaver and notification-listener labels in the TV's own settings lists. */
    const val AMBIENT = "$SHORT Ambient"
    const val AMBIENT_NOTIFICATIONS = "$SHORT ambient notifications"

    /** "Horizon 1.4 (14)" for the About page. */
    fun versionLine(version: String): String = "$NAME $version"
}
