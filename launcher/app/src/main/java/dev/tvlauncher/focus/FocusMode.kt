package dev.tvlauncher.focus

import android.content.Context
import android.content.SharedPreferences

/**
 * Focus mode's saved state and the one place every module asks "be quiet?". The end time is
 * saved, so it survives the TV closing the launcher in the background; an expired state simply
 * reads as off. Every call swallows errors and answers "off".
 */
object FocusMode {
    private const val FILE = "focus_mode"
    private const val KEY_ENDS = "ends_at"
    private const val KEY_LENGTH = "length_index"

    /** Called on the main thread after every change (the home screen's top bar chip). */
    @Volatile var onChanged: (() -> Unit)? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun state(context: Context): FocusState = try {
        FocusState(prefs(context).getLong(KEY_ENDS, FocusState.OFF))
    } catch (e: Throwable) {
        FocusState()
    }

    fun isOn(context: Context, now: Long = System.currentTimeMillis()): Boolean = try {
        state(context).isOn(now)
    } catch (e: Throwable) {
        false
    }

    fun quiet(context: Context, now: Long = System.currentTimeMillis()): FocusModel.Quiet =
        FocusModel.quiet(state(context), now)

    /** The chosen length for the one-press toggle (minutes, null = until turned off). */
    fun length(context: Context): Int? = try {
        val i = prefs(context).getInt(KEY_LENGTH, FocusModel.DEFAULT_LENGTH_INDEX)
        FocusModel.LENGTHS.getOrElse(i) { FocusModel.LENGTHS[FocusModel.DEFAULT_LENGTH_INDEX] }
    } catch (e: Throwable) {
        FocusModel.LENGTHS[FocusModel.DEFAULT_LENGTH_INDEX]
    }

    fun lengthIndex(context: Context): Int = try {
        prefs(context).getInt(KEY_LENGTH, FocusModel.DEFAULT_LENGTH_INDEX).coerceIn(0, FocusModel.LENGTHS.size - 1)
    } catch (e: Throwable) {
        FocusModel.DEFAULT_LENGTH_INDEX
    }

    fun setLengthIndex(context: Context, i: Int) {
        try { prefs(context).edit().putInt(KEY_LENGTH, i.coerceIn(0, FocusModel.LENGTHS.size - 1)).apply() } catch (e: Throwable) { }
    }

    fun set(context: Context, s: FocusState) {
        try { prefs(context).edit().putLong(KEY_ENDS, s.endsAt).apply() } catch (e: Throwable) { }
        try { onChanged?.invoke() } catch (e: Throwable) { }
    }

    fun start(context: Context, now: Long = System.currentTimeMillis()) = set(context, FocusModel.start(now, length(context)))

    fun stop(context: Context) = set(context, FocusModel.stop())

    /** The Spotlight action: returns true when Focus is now on. */
    fun toggle(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val next = FocusModel.toggle(state(context), now, length(context))
        set(context, next)
        return next.isOn(now)
    }
}
