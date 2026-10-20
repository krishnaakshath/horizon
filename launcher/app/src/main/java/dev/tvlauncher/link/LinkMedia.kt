package dev.tvlauncher.link

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.core.LinkContract.Outcome
import dev.tvlauncher.nowplaying.NowPlayingAccess

/**
 * The phone's media controls, through the same notification-listener grant as Now Playing
 * ([NowPlayingAccess]). Text is read on request only and never stored. Never throws.
 */
object LinkMedia {
    private fun sessions(context: Context): List<MediaController>? = try {
        context.getSystemService(MediaSessionManager::class.java)
            ?.getActiveSessions(NowPlayingAccess.listenerComponent(context)).orEmpty()
    } catch (e: Exception) {
        null // no notification access
    }

    private fun chosen(context: Context): MediaController? {
        val list = sessions(context) ?: return null
        val i = LinkCommands.pickSession(list.map { c -> try { c.playbackState?.state } catch (e: Exception) { null } })
        return list.getOrNull(i)
    }

    private fun position(c: MediaController): Long {
        val s = c.playbackState ?: return 0L
        var p = s.position
        if (s.state == PlaybackState.STATE_PLAYING && s.lastPositionUpdateTime > 0) {
            p += ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
        }
        return p.coerceAtLeast(0L)
    }

    private fun duration(c: MediaController): Long =
        try { c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L } catch (e: Exception) { 0L }

    fun transport(context: Context, action: String?): Int {
        if (action == null || action !in LinkContract.MEDIA_ACTIONS) return Outcome.FAILED
        val c = chosen(context) ?: return Outcome.FAILED
        return try {
            val t = c.transportControls
            when (action) {
                LinkContract.MEDIA_PLAY_PAUSE -> {
                    val st = c.playbackState?.state
                    if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) t.pause() else t.play()
                }
                LinkContract.MEDIA_NEXT -> t.skipToNext()
                LinkContract.MEDIA_PREV -> t.skipToPrevious()
                LinkContract.MEDIA_FORWARD -> t.seekTo(LinkContract.seekTarget(position(c), duration(c), true))
                LinkContract.MEDIA_REWIND -> t.seekTo(LinkContract.seekTarget(position(c), duration(c), false))
            }
            Outcome.LAUNCHER
        } catch (e: Exception) {
            Outcome.FAILED
        }
    }

    fun nowPlaying(context: Context): Bundle {
        val out = Bundle()
        val list = sessions(context)
        out.putBoolean(LinkContract.NP_ACCESS, list != null)
        val c = chosen(context) ?: return out
        try {
            val m = c.metadata
            val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            val app = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
            } catch (e: Exception) { "" }
            title?.let { out.putString(LinkContract.NP_TITLE, LinkContract.cleanText(it).take(120)) }
            artist?.let { out.putString(LinkContract.NP_ARTIST, LinkContract.cleanText(it).take(120)) }
            out.putString(LinkContract.NP_APP, LinkContract.cleanText(app).take(40))
            val st = c.playbackState?.state
            out.putBoolean(LinkContract.NP_PLAYING, st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING)
            out.putLong(LinkContract.NP_POSITION_MS, position(c))
            out.putLong(LinkContract.NP_DURATION_MS, duration(c))
        } catch (e: Exception) {
            // Session went away mid-read: whatever was filled stays.
        }
        return out
    }
}
