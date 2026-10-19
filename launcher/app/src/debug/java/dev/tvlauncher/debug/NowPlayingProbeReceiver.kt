package dev.tvlauncher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import dev.tvlauncher.nowplaying.NowPlayingAccess
import dev.tvlauncher.nowplaying.NowPlayingPrivacy
import dev.tvlauncher.nowplaying.NowPlayingProbe
import dev.tvlauncher.nowplaying.NowPlayingSelector
import dev.tvlauncher.nowplaying.PlayState
import dev.tvlauncher.nowplaying.SessionInfo

/**
 * Debug builds only (src/debug): logs, for every active media session, which Now Playing fields
 * the app publishes — title/artist present, art as a bitmap (key and size) or a URI (scheme only)
 * or none, duration, transport actions — and which session the launcher would show. Never logs
 * titles, artists or pictures ([NowPlayingProbe.describe]). Read-only: sends nothing to any app.
 *
 * ```
 * # Start something in Spotify / YouTube / Netflix..., press HOME, then:
 * adb shell am broadcast -a dev.tvlauncher.debug.NP_PROBE -n dev.tvlauncher/.debug.NowPlayingProbeReceiver
 * adb logcat -s NowPlayingProbe
 * ```
 */
class NowPlayingProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable || intent?.action != ACTION) return
        val tag = NowPlayingProbe.TAG
        val controllers = try {
            context.getSystemService(MediaSessionManager::class.java)
                ?.getActiveSessions(NowPlayingAccess.listenerComponent(context)).orEmpty()
        } catch (e: SecurityException) {
            Log.i(tag, "no notification access: allow Now Playing first")
            return
        } catch (e: Exception) {
            Log.i(tag, "media sessions unavailable: ${e.javaClass.simpleName}")
            return
        }
        Log.i(tag, "sessions=${controllers.size} access=${NowPlayingAccess.isGranted(context)}")
        val read = controllers.mapNotNull { c -> try { sample(c) } catch (e: Exception) { null } }
        val chosen = NowPlayingSelector.choose(context.packageName, read.map { NowPlayingPrivacy.sanitize(it.second) })
        for ((s, info) in read) {
            val isChosen = chosen != null && chosen.pkg == info.pkg && chosen.state == info.state
            Log.i(tag, NowPlayingProbe.describe(s, isChosen))
        }
        if (read.isEmpty()) Log.i(tag, "nothing is publishing a media session right now")
    }

    private fun sample(c: MediaController): Pair<NowPlayingProbe.Sample, SessionInfo> {
        val md = c.metadata
        val ps = c.playbackState
        fun str(k: String) = md?.getString(k)?.takeIf { it.isNotBlank() }
        val keys = listOf(
            "ALBUM_ART" to MediaMetadata.METADATA_KEY_ALBUM_ART,
            "ART" to MediaMetadata.METADATA_KEY_ART,
            "DISPLAY_ICON" to MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )
        val bmp = keys.firstNotNullOfOrNull { (name, k) -> md?.getBitmap(k)?.let { name to it } }
        val uri = str(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: str(MediaMetadata.METADATA_KEY_ART_URI)
            ?: str(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val state = ps?.state ?: PlaybackState.STATE_NONE
        val s = NowPlayingProbe.Sample(
            pkg = c.packageName,
            state = state,
            hasTitle = str(MediaMetadata.METADATA_KEY_TITLE) != null,
            hasArtist = (str(MediaMetadata.METADATA_KEY_ARTIST) ?: str(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)) != null,
            hasAlbum = str(MediaMetadata.METADATA_KEY_ALBUM) != null,
            hasDisplayTitle = str(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) != null,
            bitmapKey = bmp?.first,
            bitmapW = bmp?.second?.width ?: 0,
            bitmapH = bmp?.second?.height ?: 0,
            artUri = uri,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            actions = ps?.actions ?: 0L,
            hasSessionActivity = c.sessionActivity != null
        )
        // Same shape the tracker reads, for the selection verdict (stays in memory).
        val info = SessionInfo(
            c.packageName, null,
            str(MediaMetadata.METADATA_KEY_TITLE) ?: str(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            str(MediaMetadata.METADATA_KEY_ARTIST) ?: str(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: str(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            null, PlayState.fromPlatform(state), 0L, 0f, ps?.lastPositionUpdateTime ?: 0L, 0L, s.actions, uri
        )
        return s to info
    }

    companion object {
        const val ACTION = "dev.tvlauncher.debug.NP_PROBE"
    }
}
