package dev.tvlauncher.volume.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * The media stream through AudioManager. Never passes FLAG_SHOW_UI (flags are 0), so the
 * system's volume popup is not asked for. On a TV with an HDMI-ARC/CEC sound system Android's
 * AudioService itself forwards these changes to the receiver; when it cannot (full-volume
 * output, vendor middleware) the read-back in [VolumeController] sees no change and the key goes
 * to the system instead.
 *
 * HdmiControlManager (direct CEC volume) is a system API guarded by HDMI_CEC, a
 * signature|privileged permission, so a sideloaded app cannot use it; it is not a backend here.
 */
abstract class StreamBackend(context: Context) : VolumeBackend {
    protected val am: AudioManager? = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun read(): VolumeState? {
        val a = am ?: return null
        val max = a.getStreamMaxVolume(STREAM)
        if (max <= 0) return null
        return VolumeState(a.getStreamVolume(STREAM), max, a.isStreamMute(STREAM))
    }

    override fun canControl(): Boolean = am?.isVolumeFixed == false

    protected fun mute(on: Boolean) {
        am?.adjustStreamVolume(STREAM, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    override fun write(from: VolumeState, to: VolumeState) {
        if (from.muted && !to.muted) mute(false)
        if (to.level != from.level) setLevel(from, to.level)
        if (!from.muted && to.muted) mute(true)
    }

    protected abstract fun setLevel(from: VolumeState, level: Int)

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC

        /** Fixed flag plus the connected output types: a new soundbar or headset is a new route. */
        fun routeKey(context: Context): String {
            val a = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "none"
            val types = a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.distinct().sorted()
            return (if (a.isVolumeFixed) "fixed:" else "var:") + types.joinToString(",")
        }

        fun outputTypes(context: Context): List<Int> {
            val a = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
            return a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(AudioDeviceInfo::getType)
        }
    }
}

/** setStreamVolume to the exact level (the first choice). */
class AbsoluteStreamBackend(context: Context) : StreamBackend(context) {
    override val id = "stream"
    override fun setLevel(from: VolumeState, level: Int) {
        am?.setStreamVolume(STREAM, level, 0)
    }
}

/** adjustStreamVolume one index at a time: for builds that ignore absolute sets but follow adjusts. */
class RelativeStreamBackend(context: Context) : StreamBackend(context) {
    override val id = "adjust"
    override fun setLevel(from: VolumeState, level: Int) {
        val a = am ?: return
        val dir = if (level > from.level) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(minOf(MAX_ADJUSTS, kotlin.math.abs(level - from.level))) { a.adjustStreamVolume(STREAM, dir, 0) }
    }

    private companion object {
        const val MAX_ADJUSTS = 10
    }
}

object VolumeBackends {
    /** The default chain: exact set, then relative adjust; nothing else is open to apps. */
    fun default(context: Context): VolumeController = VolumeController(
        listOf(AbsoluteStreamBackend(context), RelativeStreamBackend(context)),
        routeKey = { StreamBackend.routeKey(context) },
    )
}
