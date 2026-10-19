package dev.tvlauncher.volume.core


/**
 * Turns the system's volume broadcasts into "show the cover with this state" (pure, JVM-tested).
 *
 * The TV sends several broadcasts per key press (VOLUME_CHANGED_ACTION for the media stream, for
 * its aliases and per output device, plus STREAM_MUTE_CHANGED_ACTION), and none of them carries
 * the flags that tell whether the TV's popup was asked for. So:
 *
 * - only the media stream counts ([stream]; -1 = the broadcast did not say, e.g. master mute);
 * - the state shown is always re-read from AudioManager by the caller ([current]), never taken
 *   from the extras, so duplicates and out-of-order broadcasts cannot show a stale number;
 * - a broadcast that leaves the state as it was last seen is a duplicate and shows nothing;
 * - changes made by our own UI (Settings > Volume & output, the launcher's own bar) pass
 *   flags 0, so the TV draws no popup and there is nothing to cover: [noteOwnChange] opens a short
 *   window in which changes only update [lastSeen].
 */
class CoverDecider(private val stream: Int = STREAM_MUSIC, private val ownWindowMs: Long = OWN_WINDOW_MS) {
    var lastSeen: VolumeState? = null
        private set
    private var ownUntil = Long.MIN_VALUE

    /** The state when we start listening (no cover for it). */
    fun prime(state: VolumeState?) {
        if (state != null) lastSeen = state
    }

    fun noteOwnChange(now: Long) {
        ownUntil = now + ownWindowMs
    }

    /** The state to show for a broadcast about [streamType], or null to leave the screen alone. */
    fun decide(streamType: Int, current: VolumeState?, now: Long): VolumeState? {
        if (streamType != ANY_STREAM && streamType != stream) return null
        if (current == null || current.max <= 0) return null
        val prev = lastSeen
        lastSeen = current
        if (now < ownUntil) return null
        if (prev == current) return null
        return current
    }

    companion object {
        const val STREAM_MUSIC = 3 // AudioManager.STREAM_MUSIC
        const val ANY_STREAM = -1

        /** Our own writes broadcast within a few ms; 400 ms also covers a held Settings stepper. */
        const val OWN_WINDOW_MS = 400L
    }
}
