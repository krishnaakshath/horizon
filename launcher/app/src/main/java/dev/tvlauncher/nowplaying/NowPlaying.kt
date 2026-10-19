package dev.tvlauncher.nowplaying

import dev.tvlauncher.discover.AppCategory
import dev.tvlauncher.discover.PopularApps
import dev.tvlauncher.ui.BrandColors

/** Playback state, collapsed from android.media.session.PlaybackState. */
enum class PlayState {
    NONE, STOPPED, PAUSED, PLAYING, BUFFERING, ERROR;

    val isActive: Boolean get() = this == PLAYING || this == BUFFERING

    companion object {
        /** Maps PlaybackState.STATE_* ints (pinned here so this stays JVM-testable). */
        fun fromPlatform(state: Int): PlayState = when (state) {
            1 -> STOPPED
            2 -> PAUSED
            3, 4, 5, 9, 10, 11 -> PLAYING // playing, fast-forward, rewind, skipping, queue item
            6, 8 -> BUFFERING // buffering, connecting
            7 -> ERROR
            else -> NONE
        }
    }
}

/** Identity of the album art for caching: same key = same picture, no re-decode. toString is redacted. */
data class ArtKey(val pkg: String, val title: String?, val album: String?, val uri: String?) {
    override fun toString() = "ArtKey(pkg=$pkg)"
}

/**
 * The media session the launcher shows. Content fields (title/artist/album/art) live in memory
 * only; [toString] is redacted so they can never reach a log by accident. See [NowPlayingPrivacy].
 */
data class NowPlaying(
    val pkg: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val art: ArtKey?,
    val state: PlayState,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
    val canPlayPause: Boolean,
    val canNext: Boolean,
    val canPrev: Boolean,
    val speed: Float = 1f
) {
    fun positionAt(nowMs: Long): Long = ProgressMath.positionNow(state, positionMs, updatedAtMs, speed, nowMs, durationMs)

    override fun toString() = "NowPlaying(pkg=$pkg, state=$state)"
}

/** One active media session, as read from a MediaController. toString is redacted. */
data class SessionInfo(
    val pkg: String,
    val appLabel: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val state: PlayState,
    val positionMs: Long,
    val speed: Float,
    /** PlaybackState.lastPositionUpdateTime (elapsedRealtime base). */
    val updatedAtMs: Long,
    val durationMs: Long,
    val actions: Long,
    val artUri: String?
) {
    val hasMetadata: Boolean get() = !title.isNullOrBlank() || !artist.isNullOrBlank()

    fun toNowPlaying(): NowPlaying = NowPlaying(
        pkg = pkg,
        appLabel = appLabel?.takeIf { it.isNotBlank() } ?: pkg,
        title = title, artist = artist, album = album,
        art = ArtKey(pkg, title, album, artUri),
        state = state, positionMs = positionMs, durationMs = durationMs, updatedAtMs = updatedAtMs,
        canPlayPause = TransportAction.canDo(actions, TransportAction.PLAY_PAUSE),
        canNext = TransportAction.canDo(actions, TransportAction.NEXT),
        canPrev = TransportAction.canDo(actions, TransportAction.PREV),
        speed = speed
    )

    override fun toString() = "SessionInfo(pkg=$pkg, state=$state)"
}

/** Transport buttons and their PlaybackState.ACTION_* bits. */
enum class TransportAction {
    PLAY_PAUSE, NEXT, PREV;

    companion object {
        const val PLATFORM_PAUSE = 1L shl 1
        const val PLATFORM_PLAY = 1L shl 2
        const val PLATFORM_SKIP_TO_PREVIOUS = 1L shl 4
        const val PLATFORM_SKIP_TO_NEXT = 1L shl 5
        const val PLATFORM_PLAY_PAUSE = 1L shl 9

        fun canDo(actions: Long, action: TransportAction): Boolean = when (action) {
            PLAY_PAUSE -> actions and (PLATFORM_PLAY_PAUSE or PLATFORM_PLAY or PLATFORM_PAUSE) != 0L
            NEXT -> actions and PLATFORM_SKIP_TO_NEXT != 0L
            PREV -> actions and PLATFORM_SKIP_TO_PREVIOUS != 0L
        }
    }
}

/** Which apps are "audio" (preferred when several sessions play at once). */
object NowPlayingApps {
    private val EXTRA_AUDIO = setOf(
        "com.spotify.music", "com.spotify.tv.android", "com.google.android.apps.youtube.music",
        "com.google.android.youtube.tvmusic", "com.amazon.mp3", "com.amazon.music.tv", "com.apple.android.music",
        "com.pandora.android", "com.pandora.android.atv", "deezer.android.app", "deezer.android.tv",
        "com.aspiro.tidal", "com.soundcloud.android", "tunein.player", "radiotime.player",
        "com.jio.media.jiobeats", "com.gaana", "com.audible.application", "com.clearchannel.iheartradio.controller",
        "au.com.shiftyjelly.pocketcasts", "de.danoeh.antennapod", "com.bambuna.podcastaddict"
    )

    fun isAudio(pkg: String): Boolean = pkg in EXTRA_AUDIO || PopularApps.byPackage(pkg)?.category == AppCategory.MUSIC
}

/** Accent of the playing app: known brand → catalog → neutral slate. */
object NowPlayingAccent {
    fun forPackage(pkg: String): Int = PopularApps.brandColorFor(pkg) ?: BrandColors.NEUTRAL
}

/** Picks the one session to show. */
object NowPlayingSelector {
    const val OWN_PACKAGE = "dev.tvlauncher"

    /**
     * Ignores our own package, sessions without title/artist, and stopped/none/error sessions.
     * Then: playing (or buffering) over paused, audio apps over video apps, most recent first.
     */
    fun choose(sessions: List<SessionInfo>, ownPackage: String = OWN_PACKAGE): SessionInfo? = sessions
        .filter { it.pkg != ownPackage && it.hasMetadata && (it.state.isActive || it.state == PlayState.PAUSED) }
        .maxWithOrNull(
            compareBy<SessionInfo> { if (it.state.isActive) 1 else 0 }
                .thenBy { if (NowPlayingApps.isAudio(it.pkg)) 1 else 0 }
                .thenBy { it.updatedAtMs }
        )
}

/** Position extrapolation for the progress bar. */
object ProgressMath {
    /**
     * [position] advanced by (now - updatedAt) x speed while playing; frozen otherwise.
     * Never negative, never past [duration] when it is known (> 0), never rewinds when the clock
     * goes backwards.
     */
    fun positionNow(state: PlayState, position: Long, updatedAt: Long, speed: Float, now: Long, duration: Long): Long {
        var p = position.coerceAtLeast(0L)
        if (state == PlayState.PLAYING && speed != 0f && !speed.isNaN()) {
            // From the raw position; negative speed = rewinding. Double math, clamped once at
            // the end, so there is no Long overflow either way.
            val elapsed = (now - updatedAt).coerceAtLeast(0L)
            val next = position.toDouble() + elapsed.toDouble() * speed
            p = next.coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
        }
        return if (duration > 0) p.coerceAtMost(duration) else p
    }

    fun fraction(position: Long, duration: Long): Float =
        if (duration <= 0 || position <= 0) 0f else (position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
}
