package dev.tvlauncher.ui

import dev.tvlauncher.apps.AppEntry
import java.text.Normalizer
import java.util.Locale

/**
 * Ranks apps for the Spotlight search field. Tiers, best first: exact label, label prefix,
 * prefix of a later word in the label, label substring, package-name substring. Matching ignores
 * case and diacritics; apps in the same tier keep their input order. At most [LIMIT] results.
 */
object SpotlightSearch {
    const val LIMIT = 12

    private const val EXACT = 0
    private const val PREFIX = 1
    private const val WORD_PREFIX = 2
    private const val SUBSTRING = 3
    private const val PACKAGE = 4
    private val MARKS = Regex("\\p{Mn}+")
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(s: String): String =
        MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim()

    fun rank(query: String, apps: List<AppEntry>): List<AppEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return apps.asSequence()
            .mapNotNull { app -> tier(q, app)?.let { it to app } }
            .sortedBy { it.first } // stable: ties keep input order
            .take(LIMIT)
            .map { it.second }
            .toList()
    }

    private fun tier(q: String, app: AppEntry): Int? {
        val label = normalize(app.label)
        return when {
            label == q -> EXACT
            label.startsWith(q) -> PREFIX
            label.split(WORD_SPLIT).drop(1).any { it.startsWith(q) } -> WORD_PREFIX
            label.contains(q) -> SUBSTRING
            app.packageName.lowercase(Locale.ROOT).contains(q) -> PACKAGE
            else -> null
        }
    }
}
