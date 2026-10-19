package dev.tvlauncher.nowplaying

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import dev.tvlauncher.ambient.NotificationAccess

/**
 * Now Playing has no permission of its own: MediaSessionManager.getActiveSessions() is allowed
 * for an enabled notification listener, so it reuses the ambient listener component and the same
 * one-time user grant (NotificationAccessCard in Settings, the setup checklist, or the one-time
 * setup helper; see docs/PROVISIONING.md).
 */
object NowPlayingAccess {
    const val LISTENER_CLASS = "dev.tvlauncher.ambient.AmbientNotificationService"

    fun listenerComponent(context: Context): ComponentName = ComponentName(context, LISTENER_CLASS)

    fun isGranted(context: Context): Boolean = NotificationAccess.isEnabled(context)
}

/** Sends transport commands to a session. Never throws. */
object NowPlayingControls {
    fun send(controller: MediaController?, action: TransportAction): Boolean {
        val c = controller ?: return false
        return try {
            val t = c.transportControls
            when (action) {
                TransportAction.PLAY_PAUSE -> {
                    val s = c.playbackState?.state ?: PlaybackState.STATE_NONE
                    if (PlayState.fromPlatform(s).isActive) t.pause() else t.play()
                }
                TransportAction.NEXT -> t.skipToNext()
                TransportAction.PREV -> t.skipToPrevious()
            }
            true
        } catch (e: Exception) {
            // Session died between the press and the call, or the app rejected it.
            false
        }
    }
}
