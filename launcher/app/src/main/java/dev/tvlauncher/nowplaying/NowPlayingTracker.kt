package dev.tvlauncher.nowplaying

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Follows the TV's active media sessions and reports the one worth showing
 * ([NowPlayingSelector]). Call [start] from onStart and [stop] from onStop.
 *
 * - Needs notification-listener access for [listenerComponent] (the ambient listener, see
 *   [NowPlayingAccess]). Without it getActiveSessions throws SecurityException: the tracker then
 *   reports [onAccessChanged] (false) and `onChanged(null)`, and never crashes.
 * - One MediaController.Callback per session; all are unregistered on every session-list change
 *   and on [stop].
 * - Delivered on the main thread, at most once per [MIN_EMIT_MS] (trailing emit keeps the latest).
 * - Album art (metadata bitmap, else ART/ALBUM_ART URI via ContentResolver) is decoded off the main
 *   thread with [ArtPlanner] (≤ 512 px). Bitmaps are never recycled by the tracker: the old one is
 *   simply dropped for the GC, so a view still drawing it is never hit by a recycled bitmap.
 * - Privacy ([NowPlayingPrivacy]): text and art are kept in memory only, never logged or saved,
 *   and dropped on [stop] or when access is lost.
 */
class NowPlayingTracker(
    context: Context,
    private val listenerComponent: ComponentName,
    private val onChanged: (NowPlaying?) -> Unit,
    private val onArt: (ArtKey?, Bitmap?) -> Unit = { _, _ -> },
    private val onAccessChanged: (Boolean) -> Unit = {}
) {
    companion object {
        const val MIN_EMIT_MS = 500L
        private val decoder: ExecutorService by lazy {
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "np-art").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
            }
        }
    }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val limiter = EmitRateLimiter(MIN_EMIT_MS)
    private val tracked = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val labels = HashMap<String, String>()
    private var manager: MediaSessionManager? = null
    private var started = false
    private var generation = 0
    private var emitScheduled = false
    private var lastEmitted: NowPlaying? = null
    private var chosen: MediaController? = null
    private var artKey: ArtKey? = null
    private var art: Bitmap? = null
    private var artPending = false
    private val artFailures = ArtFailureMemo()
    private val positionClock = PositionClock()
    private var accessKnown: Boolean? = null

    /** False after a SecurityException (no notification access). */
    var accessGranted: Boolean = true
        private set

    /** The current item, or null. Main thread. */
    val current: NowPlaying? get() = lastEmitted

    /** The current album art (≤ 512 px), or null. Main thread. */
    val currentArt: Bitmap? get() = art

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (started) track(controllers.orEmpty())
    }

    fun start() {
        if (started) return
        started = true
        generation++
        val m = try { app.getSystemService(MediaSessionManager::class.java) } catch (e: Exception) { null }
        manager = m
        if (m == null) { accessLost(); return }
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            setAccess(true)
            track(m.getActiveSessions(listenerComponent).orEmpty())
        } catch (e: SecurityException) {
            accessLost()
        } catch (e: Exception) {
            accessLost()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        generation++
        try { manager?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) { }
        manager = null
        untrackAll()
        main.removeCallbacksAndMessages(null)
        emitScheduled = false
        limiter.reset()
        clearContent()
    }

    /**
     * Retries after missing access: if started without access and the user has since granted it
     * (checked with [NowPlayingAccess.isGranted]), re-subscribes. Cheap when nothing changed.
     */
    fun retryIfAccessGranted() {
        if (!started || accessGranted) return
        if (!NowPlayingAccess.isGranted(app)) return
        stop()
        start()
    }

    /** Sends a transport command to the shown session. */
    fun send(action: TransportAction): Boolean = NowPlayingControls.send(chosen, action)

    // --- sessions ---------------------------------------------------------------------------------

    private fun track(controllers: List<MediaController>) {
        untrackAll()
        for (c in controllers) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleEmit()
                override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleEmit()
                override fun onSessionDestroyed() {
                    untrack(c)
                    scheduleEmit()
                }
            }
            try {
                c.registerCallback(cb, main)
                tracked[c.sessionToken] = c to cb
            } catch (e: Exception) {
                // Session already gone.
            }
        }
        scheduleEmit()
    }

    private fun untrack(c: MediaController) {
        positionClock.forget(c.sessionToken)
        val entry = tracked.remove(c.sessionToken) ?: return
        try { entry.first.unregisterCallback(entry.second) } catch (e: Exception) { }
    }

    private fun untrackAll() {
        tracked.values.forEach { (c, cb) -> try { c.unregisterCallback(cb) } catch (e: Exception) { } }
        tracked.clear()
        chosen = null
    }

    private fun scheduleEmit() {
        if (!started || emitScheduled) return
        val delay = limiter.delayFor(SystemClock.elapsedRealtime())
        emitScheduled = true
        main.postDelayed({
            emitScheduled = false
            if (started) emitNow()
        }, delay)
    }

    private fun emitNow() {
        val infos = tracked.values.mapNotNull { (c, _) -> read(c)?.let { NowPlayingPrivacy.sanitize(it) to c } }
        val pick = NowPlayingSelector.choose(app.packageName, infos.map { it.first })
        chosen = infos.firstOrNull { it.first === pick }?.second
        val n = pick?.toNowPlaying()
        limiter.emitted(SystemClock.elapsedRealtime())
        // New art identity, or same identity whose bitmap arrived later (apps often set art late).
        // A key whose decode failed is not retried until the key changes ([artFailures]).
        if (n?.art != artKey || (n?.art != null && art == null && !artPending && artFailures.shouldTry(n.art))) {
            loadArt(n?.art, chosen)
        }
        deliver(n)
    }

    private fun deliver(n: NowPlaying?) {
        if (n == lastEmitted) return
        lastEmitted = n
        onChanged(n)
    }

    private fun read(c: MediaController): SessionInfo? = try {
        val md = c.metadata
        val ps = c.playbackState
        val pkg = c.packageName
        val state = PlayState.fromPlatform(ps?.state ?: PlaybackState.STATE_NONE)
        val platformTime = ps?.lastPositionUpdateTime ?: 0L
        val timeKnown = PositionClock.isKnown(platformTime)
        SessionInfo(
            pkg = pkg,
            appLabel = label(pkg),
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            state = state,
            positionMs = ps?.position ?: 0L,
            // Unknown update time: show the reported position without extrapolating.
            speed = if (timeKnown) ps?.playbackSpeed ?: 0f else 0f,
            updatedAtMs = positionClock.updatedAt(c.sessionToken, state, platformTime, SystemClock.elapsedRealtime()),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            actions = ps?.actions ?: 0L,
            artUri = md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        )
    } catch (e: Exception) {
        null
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            val pm = app.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        } catch (e: Exception) {
            pkg
        }
    }

    // --- art ----------------------------------------------------------------------------------------

    private fun loadArt(key: ArtKey?, controller: MediaController?) {
        if (key != artKey) {
            artKey = key
            art = null
            onArt(key, null)
        }
        if (key == null || controller == null) return
        val md = try { controller.metadata } catch (e: Exception) { null }
        val bmp = md?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        val uri = key.uri
        if (bmp == null && uri == null) return
        val gen = generation
        artPending = true
        decoder.execute {
            val out = try { if (bmp != null) downscale(bmp) else decodeUri(uri!!) } catch (e: Throwable) { null }
            main.post {
                if (gen != generation || artKey != key) return@post
                artPending = false
                if (out == null) artFailures.failed(key)
                if (started && out != null) {
                    art = out
                    onArt(key, out)
                }
            }
        }
    }

    private fun downscale(src: Bitmap): Bitmap? {
        if (src.width <= 0 || src.height <= 0) return null
        if (ArtPlanner.plan(src.width, src.height) is ArtPlan.Reject) return null
        val (w, h) = ArtPlanner.scaledSize(src.width, src.height)
        return if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun decodeUri(uri: String): Bitmap? {
        val u = Uri.parse(uri)
        val cr = app.contentResolver
        val decoded = ArtDecode.run(
            open = { cr.openInputStream(u) },
            readBounds = { s ->
                // Bounds-only pass: decodeStream returns null here by design; only the size matters.
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(s, null, o)
                o.outWidth to o.outHeight
            },
            decode = { s, n ->
                val o = BitmapFactory.Options().apply { inSampleSize = n; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeStream(s, null, o)
            }
        ) ?: return null
        return downscale(decoded)
    }

    // --- access / privacy ---------------------------------------------------------------------------

    private fun setAccess(granted: Boolean) {
        accessGranted = granted
        if (accessKnown == granted) return
        accessKnown = granted
        onAccessChanged(granted)
    }

    private fun accessLost() {
        untrackAll()
        val hadItem = lastEmitted != null
        clearContent() // emits null when something was shown
        setAccess(false)
        if (!hadItem) onChanged(null)
    }

    private fun clearContent() {
        val hadArt = art != null || artKey != null
        art = null
        artKey = null
        artPending = false
        artFailures.reset()
        positionClock.clear()
        chosen = null
        labels.clear()
        if (lastEmitted != null) {
            lastEmitted = null
            onChanged(null)
        }
        if (hadArt) onArt(null, null)
    }
}
