package dev.tvlauncher.routines

import android.content.Context
import android.media.AudioManager
import dev.tvlauncher.ambient.WallpaperStore
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomPrefs

/** Pure Bedtime rules (JVM-tested). */
object BedtimeModel {
    /** Bedtime mode ends by itself after this long (the next morning at the latest). */
    const val MAX_MS = 12 * 60 * 60_000L
    /** Ambient starts after this many idle minutes while Bedtime is on (never later than the owner's own choice). */
    const val IDLE_MIN = 5

    fun isActive(startedAt: Long, now: Long): Boolean = startedAt > 0 && now >= startedAt && now - startedAt < MAX_MS

    fun idleDuring(current: Int): Int = minOf(current, IDLE_MIN)
}

/**
 * Bedtime mode, one press: room lighting to Dark room, the volume one step down, ambient after a
 * short idle, and no device cards. Off (or 12 hours later) puts room lighting and the idle time
 * back. State lives in the shared "routines" file; room and ambient values are the active profile's.
 */
object Bedtime {
    private const val FILE = "routines"

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOn(context: Context): Boolean = try {
        BedtimeModel.isActive(sp(context).getLong("bedtimeAt", 0L), System.currentTimeMillis())
    } catch (e: Exception) {
        false
    }

    /** Device cards stay quiet while Bedtime mode is on. */
    fun quiet(context: Context): Boolean = isOn(context)

    fun toggle(context: Context): Boolean { if (isOn(context)) off(context) else on(context); return isOn(context) }

    fun on(context: Context) {
        if (isOn(context)) return
        try {
            val room = RoomPrefs(context)
            val r = room.load()
            val wall = WallpaperStore(context)
            val w = wall.load()
            sp(context).edit().putLong("bedtimeAt", System.currentTimeMillis())
                .putString("roomBefore", r.choice.name).putInt("idleBefore", w.idleMinutes).apply()
            room.save(r.copy(choice = RoomChoice.CINEMA))
            wall.save(w.copy(idleMinutes = BedtimeModel.idleDuring(w.idleMinutes)))
            if (!dev.tvlauncher.system.MonkeyGuard.blocks()) (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
        }
        RoutineSignals.fire()
    }

    fun off(context: Context) {
        try {
            val p = sp(context)
            if (p.getLong("bedtimeAt", 0L) == 0L) return
            RoomChoice.parse(p.getString("roomBefore", null))?.let { c -> RoomPrefs(context).let { it.save(it.load().copy(choice = c)) } }
            val idle = p.getInt("idleBefore", -1)
            if (idle > 0) WallpaperStore(context).let { it.save(it.load().copy(idleMinutes = idle)) }
            p.edit().putLong("bedtimeAt", 0L).remove("roomBefore").remove("idleBefore").apply()
        } catch (e: Exception) {
        }
        RoutineSignals.fire()
    }

    /** Home's onStart: a Bedtime mode that ran out puts the settings back. */
    fun onHomeStart(context: Context) {
        try {
            if (sp(context).getLong("bedtimeAt", 0L) != 0L && !isOn(context)) off(context)
        } catch (e: Exception) {
        }
    }
}
