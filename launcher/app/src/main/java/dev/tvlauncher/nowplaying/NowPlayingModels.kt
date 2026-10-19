package dev.tvlauncher.nowplaying

import org.json.JSONObject

/** "Title — Artist" text with caps (title 60, artist 40 characters including the ellipsis). */
object NowPlayingText {
    const val TITLE_MAX = 60
    const val ARTIST_MAX = 40
    const val SEPARATOR = " — "
    private val WS = Regex("\\s+")

    fun clean(s: String?): String = s?.replace(WS, " ")?.trim().orEmpty()

    fun ellipsize(s: String, max: Int): String = when {
        max <= 0 -> ""
        s.length <= max -> s
        else -> s.take(max - 1) + "…"
    }

    fun title(n: NowPlaying?): String = ellipsize(clean(n?.title), TITLE_MAX)

    fun artist(n: NowPlaying?): String = ellipsize(clean(n?.artist), ARTIST_MAX)

    fun format(n: NowPlaying?): String {
        if (n == null) return ""
        val t = title(n)
        val a = artist(n)
        return when {
            t.isNotEmpty() && a.isNotEmpty() -> t + SEPARATOR + a
            t.isNotEmpty() -> t
            else -> a
        }
    }
}

/**
 * Privacy rules for Now Playing. Only title, artist, album and art are kept, in memory only:
 * never persisted ([PERSIST]), never logged ([LOG_CONTENT]; model toString()s are redacted), and
 * dropped when the listener disconnects or the feature is off ([shouldClear]).
 */
object NowPlayingPrivacy {
    const val PERSIST = false
    const val LOG_CONTENT = false
    const val MAX_TEXT = 200
    val KEPT_FIELDS: Set<String> = setOf("title", "artist", "album", "art")
    /** Local, permission-mediated art only (no file:// paths: other apps' files are not ours to open). */
    private val ART_SCHEMES = listOf("content://", "android.resource://")

    fun shouldClear(listenerConnected: Boolean, featureEnabled: Boolean): Boolean = !listenerConnected || !featureEnabled

    private fun text(s: String?): String? = NowPlayingText.clean(s).take(MAX_TEXT).ifEmpty { null }

    /** Trims and caps text; keeps only local art URIs (the launcher has no network). */
    fun sanitize(info: SessionInfo): SessionInfo = info.copy(
        appLabel = text(info.appLabel),
        title = text(info.title),
        artist = text(info.artist),
        album = text(info.album),
        artUri = info.artUri?.trim()?.takeIf { u -> u.length <= 2048 && ART_SCHEMES.any { u.startsWith(it, ignoreCase = true) } }
    )
}

/** Small hero chip: "♪ Title — Artist", visible while playing or paused for under 10 minutes. */
object NowPlayingChipModel {
    const val PAUSED_VISIBLE_MS = 10 * 60_000L
    const val NOTE = "♪"

    data class Chip(val visible: Boolean, val text: String, val accent: Int)

    /** [now] must use the same clock as [NowPlaying.updatedAtMs] (SystemClock.elapsedRealtime). */
    fun isFresh(n: NowPlaying?, now: Long): Boolean {
        if (n == null || NowPlayingText.format(n).isEmpty()) return false
        return when {
            n.state.isActive -> true
            n.state == PlayState.PAUSED -> now - n.updatedAtMs < PAUSED_VISIBLE_MS
            else -> false
        }
    }

    fun from(n: NowPlaying?, now: Long): Chip {
        val visible = isFresh(n, now)
        return Chip(visible, if (visible) "$NOTE " + NowPlayingText.format(n) else "",
            n?.let { NowPlayingAccent.forPackage(it.pkg) } ?: dev.tvlauncher.ui.BrandColors.NEUTRAL)
    }
}

/**
 * Compact spec for the ambient screen: two text lines, art, accent. No progress bar and no
 * controls there (static content for burn-in and because ambient takes no D-pad focus).
 */
object NowPlayingAmbientModel {
    const val ART_DP = 56
    const val LINE1_SP = 18f
    const val LINE2_SP = 14f

    data class Spec(
        val visible: Boolean,
        val line1: String,
        val line2: String,
        val accent: Int,
        val showArt: Boolean,
        val showProgress: Boolean = false,
        val showControls: Boolean = false
    )

    fun from(n: NowPlaying?, now: Long, showOnAmbient: Boolean): Spec {
        if (!showOnAmbient || n == null || !NowPlayingChipModel.isFresh(n, now)) {
            return Spec(false, "", "", dev.tvlauncher.ui.BrandColors.NEUTRAL, showArt = false)
        }
        val t = NowPlayingText.title(n)
        val a = NowPlayingText.artist(n)
        val line1 = t.ifEmpty { a }
        val line2 = listOf(if (t.isEmpty()) "" else a, n.appLabel).filter { it.isNotBlank() }.joinToString(" · ")
        return Spec(true, line1, line2, NowPlayingAccent.forPackage(n.pkg), showArt = n.art != null)
    }
}

/** Pure decisions for [NowPlayingCard]. */
object NowPlayingCardModel {
    const val TICK_MS = 1_000L
    const val ART_DP = 72
    const val BUTTON_DP = 40

    enum class Icon { PLAY, PAUSE }

    fun shouldTick(visible: Boolean, n: NowPlaying?): Boolean = visible && n != null && n.state == PlayState.PLAYING

    fun playPauseIcon(n: NowPlaying?): Icon = if (n != null && n.state.isActive) Icon.PAUSE else Icon.PLAY
}

/** Minimum spacing between emissions to the UI (tracker rate limit). Not thread-safe: main thread. */
class EmitRateLimiter(private val intervalMs: Long) {
    private var last = Long.MIN_VALUE

    /** 0 = emit now; otherwise wait this long. A clock that went backwards waits a full interval. */
    fun delayFor(now: Long): Long {
        if (last == Long.MIN_VALUE) return 0L
        val since = now - last
        return when {
            since < 0 -> intervalMs
            since >= intervalMs -> 0L
            else -> intervalMs - since
        }
    }

    fun emitted(now: Long) { last = now }

    fun reset() { last = Long.MIN_VALUE }
}

sealed class ArtPlan {
    object Reject : ArtPlan() { override fun toString() = "Reject" }
    data class Sample(val n: Int) : ArtPlan()
}

/**
 * Album-art decode budget: rejects bad sizes and sources over 64 MP, then picks the smallest
 * power-of-two sample whose decoded size fits 512x512 pixels (same approach as the wallpaper
 * SampleSizePlanner, with a much smaller budget). [scaledSize] is the final ≤ 512 px box.
 */
object ArtPlanner {
    const val MAX_SIDE_PX = 512
    const val MAX_DECODED_PIXELS = 512L * 512
    const val MAX_SRC_PIXELS = 64L * 1_000_000
    const val MAX_SRC_SIDE = 65_535

    fun plan(srcW: Int, srcH: Int): ArtPlan {
        if (srcW <= 0 || srcH <= 0 || srcW > MAX_SRC_SIDE || srcH > MAX_SRC_SIDE) return ArtPlan.Reject
        if (srcW.toLong() * srcH > MAX_SRC_PIXELS) return ArtPlan.Reject
        var n = 1
        while (n < (1 shl 16) && (srcW / n).toLong() * (srcH / n) > MAX_DECODED_PIXELS) n *= 2
        return ArtPlan.Sample(n)
    }

    /** Fits (w, h) inside 512x512 keeping the aspect ratio; never upscales; each side ≥ 1. */
    fun scaledSize(w: Int, h: Int): Pair<Int, Int> {
        if (w <= MAX_SIDE_PX && h <= MAX_SIDE_PX) return w.coerceAtLeast(1) to h.coerceAtLeast(1)
        val s = minOf(MAX_SIDE_PX.toDouble() / w, MAX_SIDE_PX.toDouble() / h)
        return Math.round(w * s).toInt().coerceIn(1, MAX_SIDE_PX) to Math.round(h * s).toInt().coerceIn(1, MAX_SIDE_PX)
    }
}

/**
 * Two-pass art decode, pure so it is testable: open → [readBounds] (a bounds-only pass yields no
 * bitmap, only the size) → [ArtPlanner.plan] → open again → [decode] with the sample size.
 * Any missing stream, bad size or oversized source gives null.
 */
object ArtDecode {
    fun <T : Any> run(
        open: () -> java.io.InputStream?,
        readBounds: (java.io.InputStream) -> Pair<Int, Int>,
        decode: (java.io.InputStream, Int) -> T?
    ): T? {
        val (w, h) = open()?.use { readBounds(it) } ?: return null
        val plan = ArtPlanner.plan(w, h) as? ArtPlan.Sample ?: return null
        return open()?.use { decode(it, plan.n) }
    }
}

/** Remembers the art key whose decode failed so it is not retried on every emission. Main thread. */
class ArtFailureMemo {
    private var failed: ArtKey? = null

    fun shouldTry(key: ArtKey): Boolean = key != failed

    fun failed(key: ArtKey) { failed = key }

    fun reset() { failed = null }
}

/**
 * Update time for a session whose PlaybackState has no lastPositionUpdateTime (0 = unknown):
 * the first time this token was seen in its current state, so "paused for 10 minutes" still
 * expires and nothing is re-stamped to "now" on every read. Known platform times pass through.
 */
class PositionClock {
    private val seen = HashMap<Any, Pair<PlayState, Long>>()

    fun updatedAt(token: Any, state: PlayState, platformTime: Long, now: Long): Long {
        if (isKnown(platformTime)) { seen.remove(token); return platformTime }
        val prev = seen[token]
        if (prev != null && prev.first == state) return prev.second
        seen[token] = state to now
        return now
    }

    fun forget(token: Any) { seen.remove(token) }

    fun clear() = seen.clear()

    companion object {
        fun isKnown(platformTime: Long) = platformTime > 0
    }
}

/** Now Playing preferences; tolerant JSON. Off by default and only effective once access is granted. */
data class NowPlayingSettings(
    /** On for new installs; nothing shows until notification access exists ([effective]). */
    val nowPlayingEnabled: Boolean = true,
    val showOnHome: Boolean = true,
    val showOnAmbient: Boolean = true
) {
    fun effective(accessGranted: Boolean): NowPlayingSettings =
        if (accessGranted) this else copy(nowPlayingEnabled = false)

    fun showHome(accessGranted: Boolean) = effective(accessGranted).nowPlayingEnabled && showOnHome

    fun showAmbient(accessGranted: Boolean) = effective(accessGranted).nowPlayingEnabled && showOnAmbient

    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("enabled", nowPlayingEnabled)
        .put("showOnHome", showOnHome)
        .put("showOnAmbient", showOnAmbient)
        .toString()

    companion object {
        fun fromJson(s: String?): NowPlayingSettings {
            if (s.isNullOrBlank()) return NowPlayingSettings()
            val json = try { JSONObject(s) } catch (e: Exception) { return NowPlayingSettings() }
            val d = NowPlayingSettings()
            fun b(key: String, def: Boolean) = json.opt(key) as? Boolean ?: def
            return NowPlayingSettings(b("enabled", d.nowPlayingEnabled), b("showOnHome", d.showOnHome), b("showOnAmbient", d.showOnAmbient))
        }
    }
}
