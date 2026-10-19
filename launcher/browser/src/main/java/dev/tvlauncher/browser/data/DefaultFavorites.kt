package dev.tvlauncher.browser.data

/** First-run favorites: plain URLs only; icons are fetched from the sites at runtime. */
object DefaultFavorites {
    val list = listOf(
        Bookmark("https://www.google.com/", "Google"),
        Bookmark("https://www.youtube.com/", "YouTube"),
        Bookmark("https://www.wikipedia.org/", "Wikipedia"),
        Bookmark("https://www.reddit.com/", "Reddit"),
        Bookmark("https://github.com/", "GitHub"),
        Bookmark("https://news.ycombinator.com/", "Hacker News"),
        Bookmark("https://www.bbc.com/", "BBC"),
        Bookmark("https://weather.com/", "Weather"),
        Bookmark("https://maps.google.com/", "Maps"),
        Bookmark("https://www.twitch.tv/", "Twitch"),
        Bookmark("https://www.spotify.com/", "Spotify"),
        Bookmark("https://www.imdb.com/", "IMDb")
    )
}
