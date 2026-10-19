package dev.tvlauncher.volume.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * "Replace the TV's volume popup": whenever the media volume or mute changes, by any means, our
 * glass column ([CoverWindow]) is drawn over the place where the TV draws its own popup.
 *
 * On TCL builds the remote's volume keys go straight from the window manager to the TV's audio
 * service (logged as TclPhoneWindowManager handleKeyBeforeQueueing) and never reach an app or
 * an accessibility key filter, so the only signal is the system's VOLUME_CHANGED_ACTION /
 * STREAM_MUTE_CHANGED_ACTION broadcast, received here by a runtime receiver for as long as
 * [context] lives. The state shown is re-read from AudioManager ([CoverDecider]).
 *
 * Never in the volume path: it only listens and draws. [enabled] is read on every change, so the
 * switch acts at once; off, or on a TV whose popup place is unknown ([CoverGeometry.measuredOn]),
 * nothing is drawn and the TV's popup shows as before. Main thread only; every step is guarded.
 */
class VolumeWatcher(
    private val context: Context,
    windowType: Int,
    face: Typeface?,
    private val enabled: () -> Boolean,
    motion: () -> BarMotion,
    private val onShown: (VolumeState, Long) -> Unit = { _, _ -> },
) {
    private val main = Handler(Looper.getMainLooper())
    private val decider = CoverDecider()
    private val window = CoverWindow(context, windowType, face, motion)
    private var receiver: BroadcastReceiver? = null

    val listening: Boolean get() = receiver != null

    /** Starts listening; false when this TV has no known popup place or registering failed. */
    fun start(): Boolean {
        if (receiver != null) return true
        return try {
            if (!CoverGeometry.measuredOn(Build.MANUFACTURER, Build.BRAND)) return false
            decider.prime(read())
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = onBroadcast(intent)
            }
            val f = IntentFilter().apply {
                addAction(ACTION_VOLUME_CHANGED)
                addAction(ACTION_STREAM_MUTE_CHANGED)
                addAction(ACTION_MASTER_MUTE_CHANGED)
            }
            context.registerReceiver(r, f, null, main)
            receiver = r
            // Already muted: the TV's mute badge is on screen now.
            decider.lastSeen?.takeIf { it.muted && enabled() }?.let { window.showBadge(it) }
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun stop() {
        val r = receiver
        receiver = null
        if (r != null) try { context.unregisterReceiver(r) } catch (e: Throwable) { }
        window.hideNow()
    }

    /** Our own code is about to change the volume with flags 0 (no TV popup): nothing to cover. */
    fun noteOwnChange() {
        decider.noteOwnChange(SystemClock.uptimeMillis())
    }

    private fun onBroadcast(intent: Intent) {
        try {
            val t0 = SystemClock.uptimeMillis()
            val stream = if (intent.action == ACTION_MASTER_MUTE_CHANGED) CoverDecider.ANY_STREAM
            else intent.getIntExtra(EXTRA_STREAM_TYPE, CoverDecider.ANY_STREAM)
            val s = decider.decide(stream, read(), t0) ?: return
            if (!enabled()) {
                window.hideNow()
                return
            }
            window.show(s)
            onShown(s, t0)
        } catch (e: Throwable) {
            // Never in the way: the TV's own popup is still there.
        }
    }

    private fun read(): VolumeState? = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(st)
        if (max <= 0) null else VolumeState(am.getStreamVolume(st), max, am.isStreamMute(st))
    } catch (e: Throwable) {
        null
    }

    companion object {
        // Hidden AudioManager actions/extras (stable strings since Android 4).
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val ACTION_MASTER_MUTE_CHANGED = "android.media.MASTER_MUTE_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
