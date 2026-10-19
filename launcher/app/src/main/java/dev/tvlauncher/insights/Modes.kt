package dev.tvlauncher.insights

enum class AppMode { VIDEO, MUSIC, GAME, OTHER }

object ModeClassifier {
    // android.content.pm.ApplicationInfo.CATEGORY_* (kept as literals so this stays JVM-testable).
    const val CATEGORY_GAME = 0
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2

    private val KNOWN: Map<String, AppMode> = buildMap {
        for (p in listOf(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "in.startv.hotstar", "com.graymatrix.did",
            "com.jio.media.stb.ondemand", "com.apple.atve.androidtv.appletv", "com.plexapp.android"
        )) put(p, AppMode.VIDEO)
        put("com.google.android.youtube.tvmusic", AppMode.MUSIC)
        put("com.spotify.tv.android", AppMode.MUSIC)
        put("com.google.android.play.games", AppMode.GAME)
    }

    fun classify(pkg: String, androidCategory: Int?): AppMode = KNOWN[pkg] ?: when (androidCategory) {
        CATEGORY_GAME -> AppMode.GAME
        CATEGORY_VIDEO -> AppMode.VIDEO
        CATEGORY_AUDIO -> AppMode.MUSIC
        else -> AppMode.OTHER
    }
}

object ModeDetector {
    /** Mode of the most recently resumed package that has not been paused since. */
    const val DEFAULT_MAX_STALE_MS = 6L * 60 * 60 * 1000

    /**
     * Packages in [ignore] (SystemUI overlays, the launcher itself) are dropped first so they cannot
     * mask the real app. A RESUMED older than [maxStaleMs] before [nowMs] with no later PAUSED is stale.
     */
    fun currentMode(
        events: List<UsageEvent>,
        classify: (String) -> AppMode,
        ignore: Set<String> = DEFAULT_IGNORE,
        nowMs: Long,
        maxStaleMs: Long = DEFAULT_MAX_STALE_MS
    ): AppMode {
        var current: String? = null
        var since = 0L
        for (e in events.filter { it.pkg !in ignore }.sortedBy { it.timeMs }) {
            when (e.type) {
                UsageEvent.Type.RESUMED -> { current = e.pkg; since = e.timeMs }
                UsageEvent.Type.PAUSED -> if (current == e.pkg) current = null
            }
        }
        if (current == null || nowMs - since > maxStaleMs) return AppMode.OTHER
        return classify(current)
    }
}
