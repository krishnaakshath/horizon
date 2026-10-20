package dev.tvlauncher.nowplaying

import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.room.Contrast
import dev.tvlauncher.room.Palette
import dev.tvlauncher.ui.BrandColors
import kotlin.math.max
import kotlin.math.min

/**
 * When the home backdrop shows the playing item's art instead of the focused app's brand colours.
 *
 * - Something worth showing: Now Playing on, "Backdrop follows what's playing" on, notification
 *   access, a session PLAYING (or PAUSED for under 10 minutes, [NowPlayingChipModel.isFresh]) and
 *   a picture for it. No picture = the brand gradient stays (the normal fallback).
 * - Ruling for the shelf: moving through the tiles brings the focused tile's brand colour back
 *   for as long as focus stays in the shelf; [SHELF_HOLD_MS] after the last move there (the user
 *   just looking at the hero) the art returns. Focus outside the shelf (mini player, top bar) =
 *   art right away.
 */
object MediaBackdropPolicy {
    const val SHELF_HOLD_MS = 6_000L
    /** A focus change this soon after a key press counts as the user moving through the shelf. */
    const val KEY_TO_FOCUS_MS = 1_000L
    const val FADE_FULL_MS = 350L
    const val FADE_LITE_MS = 250L

    enum class Layer { BRAND, MEDIA }

    /** [recheckInMs]: when the answer may change on its own (shelf hold or paused expiry); null = only on events. */
    data class Decision(val layer: Layer, val recheckInMs: Long?)

    data class Input(
        val enabled: Boolean,
        val item: NowPlaying?,
        val hasArt: Boolean,
        val now: Long,
        val focusInShelf: Boolean,
        /** Last user move inside the shelf (same clock as [now]); Long.MIN_VALUE = none since Home came up. */
        val lastShelfMoveAt: Long
    )

    fun decide(i: Input): Decision {
        if (!i.enabled || !i.hasArt || !NowPlayingChipModel.isFresh(i.item, i.now)) return Decision(Layer.BRAND, null)
        val expiry = NowPlayingExpiry.delayUntilHidden(i.item, i.now)
        if (i.focusInShelf && i.lastShelfMoveAt != Long.MIN_VALUE) {
            val since = i.now - i.lastShelfMoveAt
            if (since in 0 until SHELF_HOLD_MS) {
                val hold = SHELF_HOLD_MS - since
                return Decision(Layer.BRAND, if (expiry != null) min(hold, expiry) else hold)
            }
        }
        return Decision(Layer.MEDIA, expiry)
    }

    /** Crossfade length: 350 ms FULL, 250 ms LITE, none with reduced motion (null). */
    fun fadeMs(motion: EffectsMode?): Long = when (motion) {
        null -> 0L
        EffectsMode.FULL -> FADE_FULL_MS
        EffectsMode.LITE -> FADE_LITE_MS
    }

    /** True when a focus change caused by a key at [lastKeyAt] counts as a move inside the shelf. */
    fun isShelfMove(nowInShelf: Boolean, lastKeyAt: Long, now: Long): Boolean =
        nowInShelf && lastKeyAt != Long.MIN_VALUE && now - lastKeyAt in 0..KEY_TO_FOCUS_MS
}

/** When a shown Now Playing item hides by itself (paused for [NowPlayingChipModel.PAUSED_VISIBLE_MS]). */
object NowPlayingExpiry {
    /** Milliseconds until [n] stops being fresh; null while it plays (no timer) or when it is not shown at all. */
    fun delayUntilHidden(n: NowPlaying?, now: Long): Long? {
        if (n == null || n.state != PlayState.PAUSED || !NowPlayingChipModel.isFresh(n, now)) return null
        val since = (now - n.updatedAtMs).coerceAtLeast(0L)
        return (NowPlayingChipModel.PAUSED_VISIBLE_MS - since).coerceAtLeast(1L)
    }
}

/**
 * Pixel maths of the media backdrop, pure (packed ARGB ints) so it is JVM-testable. The renderer
 * (MediaBackdrop) runs these on a worker on a picture at 1/8 (FULL) or 1/4 (LITE) of the screen.
 */
object MediaArtMath {
    /** Minimum WCAG contrast of launcher text over the art. */
    const val MIN_CONTRAST = 4.5
    /** Head-room under the exact limit for the 8-bit rounding of the darkening table. */
    const val SAFETY = 0.94
    /** FULL: blur radius at 1/8 scale (three box passes, ~ a 40 px gaussian on a 1080p screen). */
    const val BLUR_RADIUS = 5
    const val SCALE_FULL = 8
    const val SCALE_LITE = 4
    /** Uniform darkening before the contrast clamp (FULL: a soft mood, LITE: the strong scrim). */
    const val SCRIM_FULL = 0x40
    const val SCRIM_LITE = 0x99
    /** LITE accent wash from the art's dominant colour, at the top-left corner. */
    const val LITE_ACCENT_ALPHA = 0x80

    /** Widest rendered picture (a 4K screen gets the 1080p LITE size): memory stays under budget. */
    const val MAX_W = 480

    /** Size of the rendered picture for a [w] x [h] backdrop (never 0, at most [MAX_W] wide). */
    fun size(w: Int, h: Int, full: Boolean): Pair<Int, Int> {
        val s = if (full) SCALE_FULL else SCALE_LITE
        val sw = max(1, w / s)
        val sh = max(1, h / s)
        if (sw <= MAX_W) return sw to sh
        return MAX_W to max(1, Math.round(sh * MAX_W.toDouble() / sw).toInt())
    }

    /**
     * Centre crop of a [srcW] x [srcH] source with the aspect ratio of [dstW] x [dstH] ("scale to
     * cover"). Returns left, top, right, bottom in source pixels.
     */
    fun coverCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return intArrayOf(0, 0, max(srcW, 0), max(srcH, 0))
        val src = srcW.toDouble() / srcH
        val dst = dstW.toDouble() / dstH
        return if (src > dst) {
            val w = Math.round(srcH * dst).toInt().coerceIn(1, srcW)
            val l = (srcW - w) / 2
            intArrayOf(l, 0, l + w, srcH)
        } else {
            val h = Math.round(srcW / dst).toInt().coerceIn(1, srcH)
            val t = (srcH - h) / 2
            intArrayOf(0, t, srcW, t + h)
        }
    }

    /**
     * Brightest background luminance at which [text] still has [MIN_CONTRAST] over it (light text
     * on a darker background).
     */
    fun maxBackgroundLuminance(text: Int): Double = ((Contrast.luminance(text) + 0.05) / MIN_CONTRAST - 0.05).coerceAtLeast(0.0)

    /** The limit for every text colour of [p] that sits on the backdrop (hero, top bar, labels), plus [extraTexts]. */
    fun targetLuminance(p: Palette, vararg extraTexts: Int): Double {
        var t = min(maxBackgroundLuminance(p.textPrimary), maxBackgroundLuminance(p.textSecondary))
        for (c in extraTexts) t = min(t, maxBackgroundLuminance(c))
        return t
    }

    /** Linear scale factor (0..1) that brings [brightest] luminance down to [target] x [SAFETY]. */
    fun darkenFactor(brightest: Double, target: Double): Double =
        if (brightest <= 0.0) 1.0 else min(1.0, target * SAFETY / brightest).coerceAtLeast(0.0)

    /** sRGB channel table that scales linear light by [k] (luminance scales by exactly [k], before rounding). */
    fun darkenTable(k: Double): IntArray = IntArray(256) { v -> Contrast.toSrgb(Contrast.toLinear(v) * k) }

    /** Brightest relative luminance in [px]. */
    fun brightest(px: IntArray, n: Int = px.size): Double {
        var best = 0.0
        for (i in 0 until min(n, px.size)) {
            val l = Contrast.luminance(px[i])
            if (l > best) best = l
        }
        return best
    }

    /** Applies [table] to every channel of [px] (opaque output). */
    fun applyTable(px: IntArray, table: IntArray, n: Int = px.size) {
        for (i in 0 until min(n, px.size)) {
            val c = px[i]
            px[i] = (0xFF shl 24) or (table[(c shr 16) and 0xFF] shl 16) or (table[(c shr 8) and 0xFF] shl 8) or table[c and 0xFF]
        }
    }

    /** Clamps [px] so every pixel has [MIN_CONTRAST] against text needing at most [target] luminance. */
    fun clampContrast(px: IntArray, target: Double, n: Int = px.size) {
        val k = darkenFactor(brightest(px, n), target)
        if (k < 1.0) applyTable(px, darkenTable(k), n)
    }

    /** Uniform black scrim of [alpha] over [px]. */
    fun scrim(px: IntArray, alpha: Int, n: Int = px.size) {
        if (alpha <= 0) return
        val keep = 255 - alpha.coerceIn(0, 255)
        for (i in 0 until min(n, px.size)) {
            val c = px[i]
            val r = ((c shr 16) and 0xFF) * keep / 255
            val g = ((c shr 8) and 0xFF) * keep / 255
            val b = (c and 0xFF) * keep / 255
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /**
     * LITE: the art's dominant colour washed in from the top-left (alpha [LITE_ACCENT_ALPHA] there,
     * none at the bottom-right corner), like the brand gradient.
     */
    fun accentWash(px: IntArray, w: Int, h: Int, accent: Int) {
        if (w <= 0 || h <= 0 || px.size < w * h) return
        val span = (w + h - 2).coerceAtLeast(1).toFloat()
        val base = accent and 0xFFFFFF
        for (y in 0 until h) for (x in 0 until w) {
            val a = (LITE_ACCENT_ALPHA * (1f - (x + y) / span)).toInt().coerceIn(0, 255)
            if (a == 0) continue
            val i = y * w + x
            px[i] = Contrast.over((a shl 24) or base, px[i]) or (0xFF shl 24)
        }
    }

    /** Representative colours: top-left, centre, bottom-right (for the hero colour and glass tint). */
    fun stops(px: IntArray, w: Int, h: Int): IntArray {
        if (w <= 0 || h <= 0 || px.size < w * h) return intArrayOf(BrandColors.NEUTRAL, BrandColors.NEUTRAL, BrandColors.NEUTRAL)
        fun at(fx: Float, fy: Float) = px[((h - 1) * fy).toInt() * w + ((w - 1) * fx).toInt()]
        return intArrayOf(at(0.15f, 0.2f), at(0.5f, 0.5f), at(0.85f, 0.85f))
    }
}

/**
 * Settings > Home screen > Now Playing: the page's rows (keys, order) and their texts, pure. At
 * most [dev.tvlauncher.appsettings.PageSpec.MAX_PRIMARY] primary rows; no advanced block.
 */
object NowPlayingPageRows {
    const val ID = "home.nowPlayingPage"
    const val ENABLED = "np.enabled"
    const val MINI_PLAYER = "np.miniPlayer"
    const val BACKDROP = "np.backdrop"
    const val AMBIENT = "np.ambient"
    const val ACCESS = "np.access"
    const val STYLE = "np.style"
    const val SPOTIFY = "np.spotify"

    val PRIMARY = listOf(ENABLED, MINI_PLAYER, BACKDROP, AMBIENT, STYLE, SPOTIFY, ACCESS)
    /** Plain on/off rows; [AMBIENT] is a three-way choice ([AmbientMusicPlan.Mode]), [STYLE] a choice, [SPOTIFY] a page. */
    val TOGGLES = listOf(ENABLED, MINI_PLAYER, BACKDROP)

    const val LEAD = "Spotify, YouTube and other players show here while they play."
    const val ACCESS_TITLE = "Notification access"
    const val ACCESS_ON = "Allowed"
    const val ACCESS_OFF = "Not allowed yet"

    /** Visible rows: the three "where" toggles only while Now Playing is on. */
    fun rows(s: NowPlayingSettings): List<String> =
        if (s.nowPlayingEnabled) PRIMARY else listOf(ENABLED, ACCESS)

    fun title(key: String): String = when (key) {
        ENABLED -> "Now Playing"
        MINI_PLAYER -> "Show mini player"
        BACKDROP -> "Backdrop follows what's playing"
        AMBIENT -> "Screensaver follows the music"
        STYLE -> "Style"
        SPOTIFY -> "Spotify artwork"
        ACCESS -> ACCESS_TITLE
        else -> key
    }

    fun subtitle(key: String): String? = when (key) {
        ENABLED -> "Title, artist and art stay in memory only; never saved"
        MINI_PLAYER -> "Play, pause and skip on the home screen; OK opens the app"
        BACKDROP -> "The home background shows the album art or video picture"
        AMBIENT -> "Ambient shows the playing song: cover, title and progress"
        STYLE -> "For the home backdrop and the screensaver"
        SPOTIFY -> "Optional: your own Spotify key for artist banners"
        else -> null
    }

    fun accessValue(granted: Boolean): String = if (granted) ACCESS_ON else ACCESS_OFF

    fun toggle(s: NowPlayingSettings, key: String): NowPlayingSettings = when (key) {
        ENABLED -> s.copy(nowPlayingEnabled = !s.nowPlayingEnabled)
        MINI_PLAYER -> s.copy(showOnHome = !s.showOnHome)
        BACKDROP -> s.copy(backdropFollows = !s.backdropFollows)
        AMBIENT -> s.copy(showOnAmbient = !s.showOnAmbient)
        else -> s
    }

    fun isOn(s: NowPlayingSettings, key: String): Boolean = when (key) {
        ENABLED -> s.nowPlayingEnabled
        MINI_PLAYER -> s.showOnHome
        BACKDROP -> s.backdropFollows
        AMBIENT -> s.showOnAmbient
        else -> false
    }

    /** Value of the "Now Playing" row on the Home screen page. */
    fun summary(s: NowPlayingSettings): String = if (s.nowPlayingEnabled) "On" else "Off"
}

/**
 * One line per media session for the debug-only Now Playing probe (src/debug NowPlayingProbeReceiver).
 * Content is never logged: only which fields exist, the art's kind and size, and the URI scheme.
 */
object NowPlayingProbe {
    const val TAG = "NowPlayingProbe"

    data class Sample(
        val pkg: String,
        val state: Int,
        val hasTitle: Boolean,
        val hasArtist: Boolean,
        val hasAlbum: Boolean,
        val hasDisplayTitle: Boolean,
        /** Which metadata bitmap key is set (ALBUM_ART, ART, DISPLAY_ICON) or null. */
        val bitmapKey: String?,
        val bitmapW: Int,
        val bitmapH: Int,
        /** First art URI found, or null. Only its scheme is printed. */
        val artUri: String?,
        val durationMs: Long,
        val actions: Long,
        val hasSessionActivity: Boolean
    )

    fun scheme(uri: String?): String {
        if (uri.isNullOrBlank()) return "none"
        val i = uri.indexOf(':')
        return if (i <= 0) "unknown" else uri.substring(0, i).lowercase()
    }

    /** What the launcher would do with this session's art: bitmap, local URI, or nothing (no network). */
    fun artVerdict(s: Sample): String = when {
        s.bitmapKey != null -> "bitmap"
        s.artUri != null && NowPlayingPrivacy.sanitize(info(s)).artUri != null -> "uri"
        s.artUri != null -> "uri-rejected(${scheme(s.artUri)})"
        else -> "none"
    }

    private fun info(s: Sample) = SessionInfo(s.pkg, null, null, null, null, PlayState.fromPlatform(s.state), 0, 0f, 0, 0, s.actions, s.artUri)

    fun describe(s: Sample, chosen: Boolean): String {
        val meta = listOfNotNull("title".takeIf { s.hasTitle }, "artist".takeIf { s.hasArtist },
            "album".takeIf { s.hasAlbum }, "displayTitle".takeIf { s.hasDisplayTitle }).ifEmpty { listOf("none") }
        val acts = listOfNotNull(
            "playPause".takeIf { TransportAction.canDo(s.actions, TransportAction.PLAY_PAUSE) },
            "next".takeIf { TransportAction.canDo(s.actions, TransportAction.NEXT) },
            "prev".takeIf { TransportAction.canDo(s.actions, TransportAction.PREV) }
        ).ifEmpty { listOf("none") }
        val bmp = if (s.bitmapKey != null) "${s.bitmapKey}:${s.bitmapW}x${s.bitmapH}" else "none"
        return "pkg=${s.pkg} state=${PlayState.fromPlatform(s.state)} meta=${meta.joinToString(",")} " +
            "bitmap=$bmp uri=${scheme(s.artUri)} art=${artVerdict(s)} duration=${if (s.durationMs > 0) "yes" else "no"} " +
            "actions=${acts.joinToString(",")} openable=${if (s.hasSessionActivity) "session" else "launcher"} " +
            "shown=${if (chosen) "yes" else "no"}"
    }
}
