package dev.tvlauncher.helper.volume

/** The value line of the helper's "Use launcher volume bar" row (pure). */
object VolumeStatus {
    fun line(enabled: Boolean, keysSeen: Int, keysTaken: Int): String = when {
        !enabled -> "Off: the TV shows its own volume popup"
        keysSeen == 0 -> "On. Press a volume key on the remote to try it"
        keysTaken == 0 -> "On, but this TV keeps the volume keys for itself so far"
        else -> "On"
    }
}
