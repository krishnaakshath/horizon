package dev.tvlauncher.helper

object ExclusionList {
    val DEFAULTS: Set<String> = setOf(
        "dev.tvlauncher",
        "dev.tvlauncher.helper",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.google.android.tvsettings",
    )

    private val pkg = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+$")

    /** Accepts comma, semicolon, whitespace or newline separated package names. */
    fun parse(text: String?): Set<String> =
        (text ?: "").split(',', ';', '\n', '\r', '\t')
            .map { it.trim().lowercase() }
            .filter { pkg.matches(it) }
            .toSet()

    fun format(set: Set<String>): String = set.sorted().joinToString("\n")

    fun isExcluded(foreground: String?, exclusions: Set<String>): Boolean =
        foreground.isNullOrBlank() || foreground.lowercase() in exclusions
}
