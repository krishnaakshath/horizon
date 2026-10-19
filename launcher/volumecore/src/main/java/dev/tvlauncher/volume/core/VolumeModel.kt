package dev.tvlauncher.volume.core

import android.view.KeyEvent

/** One reading of the TV's media volume: [level] in 0..[max], plus the mute flag. */
data class VolumeState(val level: Int, val max: Int, val muted: Boolean) {
    /** 0..1 for the bar (0 when muted is drawn separately). */
    val fraction: Float get() = if (max <= 0) 0f else level.coerceIn(0, max).toFloat() / max
}

/** The remote keys the volume bar takes over. */
enum class VolumeKey {
    UP, DOWN, MUTE;

    companion object {
        /** Null for every other key (KEYCODE_MUTE is the microphone key and is left alone). */
        fun of(keyCode: Int): VolumeKey? = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> DOWN
            KeyEvent.KEYCODE_VOLUME_MUTE -> MUTE
            else -> null
        }
    }
}

/**
 * Pure volume arithmetic: step size for the stream's range, hold-to-repeat acceleration,
 * clamping and the mute rules. No Android calls, so it is unit-tested on the JVM.
 */
object VolumeMath {
    /** One press moves about 1 % of a 0..100 range, one index on a short 0..15 range. */
    fun baseStep(max: Int): Int = if (max <= 0) 0 else maxOf(1, (max + 50) / 100)

    /**
     * Steps for a key event: the first press is one [baseStep]. Held keys repeat about 20 times a
     * second, so short ranges (<= 20) move only on every third repeat and mid ranges (<= 50) on every
     * second; long ranges move on every repeat and speed up the longer the key is held (x2 after
     * 8 repeats, x3 after 20). 0 means "nothing to do for this repeat".
     */
    fun delta(max: Int, repeatCount: Int): Int {
        val base = baseStep(max)
        if (base == 0) return 0
        if (repeatCount <= 0) return base
        val every = when {
            max <= 20 -> 3
            max <= 50 -> 2
            else -> 1
        }
        if (repeatCount % every != 0) return 0
        val accel = when {
            max <= 50 -> 1
            repeatCount < ACCEL_2_AFTER -> 1
            repeatCount < ACCEL_3_AFTER -> 2
            else -> 3
        }
        return base * accel
    }

    const val ACCEL_2_AFTER = 8
    const val ACCEL_3_AFTER = 20

    fun clamp(level: Int, max: Int): Int = level.coerceIn(0, maxOf(0, max))

    /**
     * Where a key takes [from]. UP/DOWN always unmute (as on every TV) and move by [delta];
     * MUTE toggles on the first press and ignores its repeats.
     */
    fun plan(from: VolumeState, key: VolumeKey, repeatCount: Int): VolumeState = when (key) {
        VolumeKey.MUTE -> if (repeatCount > 0) from else from.copy(muted = !from.muted)
        VolumeKey.UP -> {
            val d = delta(from.max, repeatCount)
            if (d == 0 && !from.muted) from else from.copy(level = clamp(from.level + d, from.max), muted = false)
        }
        VolumeKey.DOWN -> {
            val d = delta(from.max, repeatCount)
            if (d == 0 && !from.muted) from else from.copy(level = clamp(from.level - d, from.max), muted = false)
        }
    }

    /**
     * True when a press changes nothing because the level is already at its end (UP at max,
     * DOWN at 0, not muted). Such a press is only taken over once the route is known to follow
     * our changes; otherwise the TV may be on a fixed/full-volume output where our reading is
     * meaningless, and the key goes to the system.
     */
    fun atLimit(from: VolumeState, key: VolumeKey): Boolean = !from.muted && when (key) {
        VolumeKey.UP -> from.level >= from.max
        VolumeKey.DOWN -> from.level <= 0
        VolumeKey.MUTE -> false
    }
}
