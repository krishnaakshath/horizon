package dev.tvlauncher.discover

import java.util.Locale

/**
 * Pure builder for Play Store detail links. Nothing is built unless the package id is a
 * well-formed Java package name, so a hostile id can never add query parameters (`&`), fragments
 * (`#`), spaces or a second scheme to the URI.
 */
object PlayStoreLinks {
    const val PLAY_STORE_PACKAGE = "com.android.vending"
    const val UNAVAILABLE_TEXT = "Play Store isn't available on this TV"
    private const val MAX_LEN = 255
    private val CHARS = Regex("^[A-Za-z0-9_.]+$")
    private val SEGMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    enum class Kind { MARKET, WEB }

    /** One intent candidate: ACTION_VIEW on [uri], optionally restricted to [targetPackage]. */
    data class Candidate(val uri: String, val kind: Kind, val targetPackage: String?)

    fun isValidPackage(pkg: String): Boolean {
        if (pkg.isEmpty() || pkg.length > MAX_LEN || !CHARS.matches(pkg)) return false
        val parts = pkg.split('.')
        return parts.size >= 2 && parts.all { SEGMENT.matches(it) }
    }

    fun marketUri(pkg: String): String? = if (isValidPackage(pkg)) "market://details?id=$pkg" else null

    fun webUri(pkg: String): String? =
        if (isValidPackage(pkg)) "https://play.google.com/store/apps/details?id=$pkg" else null

    /**
     * Ordered candidates: market:// in the Play Store app, market:// in any handler (the TV's
     * store), then the https page (a browser). Empty for an invalid id.
     */
    fun detailsIntents(pkg: String): List<Candidate> {
        val market = marketUri(pkg) ?: return emptyList()
        val web = webUri(pkg) ?: return emptyList()
        return listOf(
            Candidate(market, Kind.MARKET, PLAY_STORE_PACKAGE),
            Candidate(market, Kind.MARKET, null),
            Candidate(web, Kind.WEB, null)
        )
    }
}

/** Region for the catalog: the user's override, else the system locale's country, else the TV's. */
object RegionGuess {
    private val ALPHA2 = Regex("^[A-Za-z]{2}$")

    fun isValid(code: String?): Boolean = code != null && ALPHA2.matches(code)

    /** Upper-case ISO alpha-2 or null. */
    fun normalize(code: String?): String? = code?.trim()?.takeIf { isValid(it) }?.uppercase(Locale.ROOT)

    /** Call with `Locale.getDefault()`; [tvCountry] is an optional second source (e.g. the TV's setup country). */
    fun fromLocale(locale: Locale, tvCountry: String?, override: String? = null): String? =
        normalize(override) ?: normalize(locale.country) ?: normalize(tvCountry)
}
