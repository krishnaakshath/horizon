package dev.tvlauncher.browser

/** Start-page quick links: plain URLs with a colored letter tile (no logos). */
object Bookmarks {
    data class Bookmark(val title: String, val url: String, val color: Int) {
        val letter: Char get() = title.first().uppercaseChar()
        val host: String get() = url.removePrefix("https://").removePrefix("www.").substringBefore('/')
    }

    val all = listOf(
        Bookmark("Google", "https://www.google.com", 0xFF4285F4.toInt()),
        Bookmark("YouTube", "https://m.youtube.com", 0xFFFF3B30.toInt()),
        Bookmark("Wikipedia", "https://en.wikipedia.org", 0xFF8E99AE.toInt()),
        Bookmark("News", "https://news.google.com", 0xFFF59E0B.toInt()),
        Bookmark("Reddit", "https://www.reddit.com", 0xFFFF5722.toInt()),
        Bookmark("GitHub", "https://github.com", 0xFF8B5CF6.toInt()),
        Bookmark("Weather", "https://weather.com", 0xFF0EA5E9.toInt()),
        Bookmark("Maps", "https://maps.google.com", 0xFF22C55E.toInt())
    )
}
