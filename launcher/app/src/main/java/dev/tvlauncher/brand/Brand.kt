package dev.tvlauncher.brand

/**
 * The product's names, in one place. The product is [NAME] (About page, notices, docs); the screen
 * shows the short mark [SHORT] (app labels, splash, setup, Safe Mode). Every module's
 * `strings.xml` app_name mirrors [SHORT] (BrandStringsTest pins them), so launcher tiles and the
 * system's app lists follow this file. Package names (`dev.tvlauncher.*`) are internal ids and
 * deliberately not part of the brand: renaming them would reset every TV's saved state.
 */
object Brand {
    // Mintaka is a star in Orion's belt. The product had another name before 1.0.1 (see CHANGELOG.md).
    const val NAME = "Mintaka"
    const val SHORT = "M02"
    /** The letter-spaced caption under the splash mark. */
    const val CAPTION = "MINTAKA"
    /** One line under the name on the About page. */
    const val TAGLINE = "A calm, fast home for your TV."
    /** Where the name comes from (About page, README). */
    const val STORY = "Named after a star in Orion's belt."
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

    /** "Mintaka 1.4 (14)" for the About page. */
    fun versionLine(version: String): String = "$NAME $version"
}
