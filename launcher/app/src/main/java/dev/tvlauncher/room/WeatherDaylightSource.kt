package dev.tvlauncher.room

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import java.util.Calendar

/**
 * Sunrise/sunset from the weather companion's `content://dev.tvlauncher.weather/current` row, when
 * (in a future version) it carries them. Accepted columns, first match wins:
 * `sunriseMinute`/`sunsetMinute` (local minutes of day) or `sunrise`/`sunset` (epoch seconds or
 * millis). Today's row has neither, so this is a dormant SEAM: it returns null and the next source
 * (the latitude estimate) is used.
 *
 * Main-thread safety: it never queries unless the authority resolves to an installed provider
 * (a cheap PackageManager lookup, cached with the result), the result is cached for [ttlMs], and
 * the controller only asks while AUTO has no light sensor. Never throws. If a future companion
 * makes the query slow, move it to a background thread and keep the cached value here.
 */
class WeatherDaylightSource(
    context: Context,
    private val ttlMs: Long = 30 * 60_000L
) : DaylightSource {
    private val resolver = context.applicationContext.contentResolver
    private val packageManager = context.applicationContext.packageManager
    private var cached: Pair<Int, Int>? = null
    private var cachedAt = Long.MIN_VALUE

    override fun sunriseSunsetMinutes(): Pair<Int, Int>? {
        val now = SystemClock.elapsedRealtime()
        if (cachedAt != Long.MIN_VALUE && now - cachedAt in 0 until ttlMs) return cached
        cached = try { if (resolvable()) query() else null } catch (e: Exception) { null }
        cachedAt = now
        return cached
    }

    private fun resolvable(): Boolean = packageManager.resolveContentProvider(AUTHORITY, 0) != null

    private fun query(): Pair<Int, Int>? {
        val c: Cursor = resolver.query(URI, null, null, null, null) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            val r = minuteCol(it, "sunriseMinute") ?: epochCol(it, "sunrise") ?: return null
            val s = minuteCol(it, "sunsetMinute") ?: epochCol(it, "sunset") ?: return null
            return if (r in 0 until 1440 && s in 0 until 1440 && r < s) r to s else null
        }
    }

    private fun minuteCol(c: Cursor, name: String): Int? {
        val i = c.getColumnIndex(name)
        if (i < 0 || c.isNull(i)) return null
        return c.getInt(i)
    }

    private fun epochCol(c: Cursor, name: String): Int? {
        val i = c.getColumnIndex(name)
        if (i < 0 || c.isNull(i)) return null
        val v = c.getLong(i)
        if (v <= 0L) return null
        val ms = if (v < 100_000_000_000L) v * 1000L else v
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    companion object {
        const val AUTHORITY = "dev.tvlauncher.weather"
        val URI: Uri = Uri.parse("content://$AUTHORITY/current")
    }
}
