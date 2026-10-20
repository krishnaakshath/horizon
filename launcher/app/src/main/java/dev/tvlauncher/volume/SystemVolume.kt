package dev.tvlauncher.volume

import android.content.Context
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.MediaRouter

/** The media volume as Settings shows it. */
data class VolumeState(val level: Int, val max: Int, val muted: Boolean)

/** Read and set the media volume for Settings (flags 0: never the system popup). Every call is guarded. */
class SystemVolume(context: Context) {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun read(): VolumeState? = try {
        val a = am
        val max = a?.getStreamMaxVolume(STREAM) ?: 0
        if (a == null || max <= 0) null else VolumeState(a.getStreamVolume(STREAM), max, a.isStreamMute(STREAM))
    } catch (e: Throwable) {
        null
    }

    val fixed: Boolean get() = try { am?.isVolumeFixed ?: true } catch (e: Throwable) { true }

    /** Sets [level] (unmuting first); true when the TV took the change. */
    fun setLevel(level: Int): Boolean = try {
        check(!dev.tvlauncher.system.MonkeyGuard.blocks())
        val a = am!!
        if (a.isStreamMute(STREAM)) a.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0)
        a.setStreamVolume(STREAM, level, 0)
        a.getStreamVolume(STREAM) == level
    } catch (e: Throwable) {
        false
    }

    /** True when the TV took the change. */
    fun setMuted(muted: Boolean): Boolean = try {
        check(!dev.tvlauncher.system.MonkeyGuard.blocks())
        val a = am!!
        a.adjustStreamVolume(STREAM, if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        a.isStreamMute(STREAM) == muted
    } catch (e: Throwable) {
        false
    }

    fun outputTypes(): List<Int> = try { am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.map(AudioDeviceInfo::getType) ?: emptyList() } catch (e: Throwable) { emptyList() }

    /** The selected media route: (name, isTheTvItself), or null when unknown. */
    fun selectedRoute(): Pair<String, Boolean>? = try {
        val mr = app.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
        val sel = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        sel.getName(app).toString() to (sel == mr.defaultRoute)
    } catch (e: Throwable) {
        null
    }

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
    }
}
