package dev.tvlauncher.nowplaying

import dev.tvlauncher.link.core.LinkContract

/** Settings > Home screen > Now Playing > Style: how the art fills the Home backdrop and the screensaver. */
enum class NowPlayingStyle(val wire: String, val label: String) {
    COVER("cover", "Cover"),
    BLURRED("blurred", "Blurred cover"),
    BANNER("banner", "Artist banner"),
    WASH("wash", "Colour wash only");

    companion object {
        fun of(s: String?): NowPlayingStyle = values().firstOrNull { it.wire == s } ?: BLURRED
    }
}

/**
 * "Screensaver follows the music" (pure). Ambient shows the playing song full screen while a
 * session is PLAYING, or PAUSED for under 10 minutes ([NowPlayingChipModel.isFresh]); otherwise the
 * normal wallpaper. [Decision.recheckInMs] is when a paused item expires on its own.
 */
object AmbientMusicPlan {
    const val FADE_MS = 350L

    data class Decision(val showSong: Boolean, val recheckInMs: Long?)

    fun decide(enabled: Boolean, item: NowPlaying?, now: Long): Decision {
        if (!enabled || item == null || !NowPlayingChipModel.isFresh(item, now)) return Decision(false, null)
        if (NowPlayingText.title(item).isEmpty() && NowPlayingText.artist(item).isEmpty()) return Decision(false, null)
        return Decision(true, NowPlayingExpiry.delayUntilHidden(item, now))
    }

    /** Crossfade for a track change: 350 ms, 0 on LITE or with reduced motion. */
    fun fadeMs(lite: Boolean, reducedMotion: Boolean): Long = if (lite || reducedMotion) 0L else FADE_MS

    /** A different song (not just a position or state update) changes the screen. */
    fun isNewTrack(old: NowPlaying?, new: NowPlaying?): Boolean =
        old?.pkg != new?.pkg || old?.title != new?.title || old?.artist != new?.artist || old?.album != new?.album

    /** Row value of "Screensaver follows the music" (three states, one row). */
    enum class Mode(val label: String) { FULL("Full screen"), CARD("Small card"), OFF("Off") }

    fun mode(s: NowPlayingSettings): Mode = when {
        s.ambientFollows -> Mode.FULL
        s.showOnAmbient -> Mode.CARD
        else -> Mode.OFF
    }

    fun withMode(s: NowPlayingSettings, m: Mode): NowPlayingSettings = when (m) {
        Mode.FULL -> s.copy(ambientFollows = true, showOnAmbient = true)
        Mode.CARD -> s.copy(ambientFollows = false, showOnAmbient = true)
        Mode.OFF -> s.copy(ambientFollows = false, showOnAmbient = false)
    }
}

/**
 * When the launcher asks H02 Link for a Spotify picture (pure). Only with a configured key, only for
 * the style that needs it (Artist banner), and only for a song with a title.
 */
object SpotifyArtPolicy {
    const val CREDIT = "Artwork: Spotify"
    /** In-memory budget for downloaded pictures in the launcher. */
    const val MEMORY_BYTES = 3 * 1024 * 1024
    const val POLL_MS = 1_500L
    const val POLL_TRIES = 8

    fun wanted(style: NowPlayingStyle, keyConfigured: Boolean, item: NowPlaying?): Boolean =
        style == NowPlayingStyle.BANNER && keyConfigured && item != null && NowPlayingText.title(item).isNotEmpty()

    fun kind(style: NowPlayingStyle): String =
        if (style == NowPlayingStyle.BANNER) LinkContract.ART_KIND_ARTIST else LinkContract.ART_KIND_ALBUM

    fun key(item: NowPlaying, style: NowPlayingStyle): String =
        LinkContract.spotifyKey(item.title, item.artist, item.album, kind(style))

    /** The artUri for the provider file; null for a key that is not ours. */
    fun uri(key: String): String? = if (LinkContract.isSpotifyKey(key)) "${LinkContract.URI_ART}/$key" else null

    /** Settings row texts. */
    fun stateText(configured: Boolean, state: String?): String = when {
        !configured -> "No key"
        state == LinkContract.SP_OK -> "Key works"
        state == LinkContract.SP_INVALID -> "Spotify refused this key"
        state == LinkContract.SP_OFFLINE -> "Can't reach Spotify right now"
        else -> "Saved, not tested yet"
    }
}

/** A bounded, byte-sized LRU (pure; the launcher keeps decoded Spotify pictures here). */
class ByteLru<V : Any>(private val budget: Int, private val sizeOf: (V) -> Int, private val onDrop: (V) -> Unit = {}) {
    private val map = LinkedHashMap<String, V>(8, 0.75f, true)
    var bytes = 0
        private set

    @Synchronized fun get(k: String): V? = map[k]

    @Synchronized fun put(k: String, v: V) {
        val size = sizeOf(v)
        if (size > budget) return
        map.remove(k)?.let { bytes -= sizeOf(it); if (it !== v) onDrop(it) }
        map[k] = v
        bytes += size
        val it = map.entries.iterator()
        while (bytes > budget && it.hasNext()) {
            val e = it.next()
            if (e.key == k) continue
            bytes -= sizeOf(e.value)
            it.remove()
            onDrop(e.value)
        }
    }

    @Synchronized fun clear() { map.values.forEach(onDrop); map.clear(); bytes = 0 }

    @Synchronized fun size() = map.size
}
