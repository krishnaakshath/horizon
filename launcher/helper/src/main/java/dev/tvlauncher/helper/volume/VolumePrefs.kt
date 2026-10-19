package dev.tvlauncher.helper.volume

import android.content.Context
import android.content.SharedPreferences
import dev.tvlauncher.volume.core.VolumeKey
import dev.tvlauncher.volume.core.VolumeResult

/**
 * The volume bar's own small store (separate from HelperPrefs): the "Use launcher volume bar"
 * switch (on by default), the launcher's last animation level, and a few counters that tell
 * whether volume keys reach the helper at all on this TV.
 */
class VolumePrefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) { sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    /** "Replace the TV's volume popup" (the launcher's Settings switch; on by default). */
    var cover: Boolean
        get() = sp.getBoolean(KEY_COVER, true)
        set(v) { sp.edit().putBoolean(KEY_COVER, v).apply() }

    /** "full", "lite", "off" or null (never told). */
    var launcherMotion: String?
        get() = sp.getString(KEY_MOTION, null)
        set(v) { sp.edit().putString(KEY_MOTION, v).apply() }

    val keysSeen: Int get() = sp.getInt(KEY_SEEN, 0)
    val keysTaken: Int get() = sp.getInt(KEY_TAKEN, 0)
    val last: String? get() = sp.getString(KEY_LAST, null)

    /** One first press of a volume key reached the service; [r] null means the switch was off. */
    fun recordSeen(key: VolumeKey, r: VolumeResult?) {
        val taken = r is VolumeResult.Handled
        val what = when (r) {
            null -> "off"
            is VolumeResult.Handled -> "ours (${r.backend})"
            is VolumeResult.Pass -> "system (${r.reason})"
        }
        sp.edit()
            .putInt(KEY_SEEN, keysSeen + 1)
            .putInt(KEY_TAKEN, keysTaken + if (taken) 1 else 0)
            .putString(KEY_LAST, "${key.name.lowercase()}: $what")
            .apply()
    }

    companion object {
        const val NAME = "volume_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_COVER = "cover"
        private const val KEY_MOTION = "launcher_motion"
        private const val KEY_SEEN = "keys_seen"
        private const val KEY_TAKEN = "keys_taken"
        private const val KEY_LAST = "last"
    }
}
