package dev.tvlauncher.ambient

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import dev.tvlauncher.R

/**
 * [WeatherSource] backed by the companion app's ContentProvider. Returns null when the provider
 * is not installed; never throws. Blocking: call from a background thread.
 */
class ContentProviderWeatherSource(context: Context) : WeatherSource {
    private val app = context.applicationContext

    override fun queryCurrent(): List<Map<String, Any?>>? = query(WeatherContract.CURRENT_URI)
    override fun queryDaily(): List<Map<String, Any?>>? = query(WeatherContract.DAILY_URI)

    override fun isInstalled(): Boolean = installed()

    private fun installed(): Boolean = try {
        app.packageManager.resolveContentProvider(WeatherContract.AUTHORITY, 0) != null
    } catch (e: Exception) {
        false
    }

    private fun query(uri: String): List<Map<String, Any?>>? {
        if (!installed()) return null
        return try {
            app.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { rows(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun rows(c: Cursor): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        val names = c.columnNames
        while (c.moveToNext() && out.size < 16) {
            val row = HashMap<String, Any?>(names.size * 2)
            for (i in names.indices) {
                row[names[i]] = when (c.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    else -> null
                }
            }
            out.add(row)
        }
        return out
    }
}

object WeatherCompanion {
    @Volatile private var shared: TimeoutWeatherSource? = null

    /** Process-wide bounded source (one worker thread, shared breaker) used by every AmbientView. */
    fun source(context: Context): TimeoutWeatherSource =
        shared ?: synchronized(this) {
            shared ?: TimeoutWeatherSource(ContentProviderWeatherSource(context.applicationContext)).also { shared = it }
        }

    /** Shared across ambient instances so re-showing ambient never spams the companion. */
    val refreshThrottle = RefreshThrottle(15 * 60_000L)

    /** At most one stale-data request per 5 min, shared by Home and Ambient. */
    val staleThrottle = RefreshThrottle(5 * 60_000L)

    /**
     * Asks the companion to refresh when [read] is older than 20 min. Explicit broadcast guarded
     * by a signature permission (only the same signer receives it); the companion applies its own
     * staleness and rate rules. Cheap, so safe on any thread.
     */
    fun requestRefreshIfStale(context: Context, read: WeatherRead, nowMs: Long = System.currentTimeMillis()) {
        if (!WeatherRefreshRequest.needed(read, nowMs)) return
        if (!staleThrottle.tryAcquire(nowMs)) return
        try {
            context.sendBroadcast(
                Intent(WeatherContract.ACTION_REFRESH_IF_STALE).setPackage(WeatherContract.PACKAGE),
                WeatherContract.PERMISSION_REFRESH
            )
        } catch (e: Exception) {
        }
    }

    /** Calls [onChange] on the main thread when the companion stores new data. Returns the stop handle. */
    fun observe(context: Context, onChange: () -> Unit): () -> Unit {
        val app = context.applicationContext
        val o = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { onChange() }
        }
        try {
            app.contentResolver.registerContentObserver(Uri.parse(WeatherContract.CURRENT_URI), false, o)
        } catch (e: RuntimeException) {
            return {}
        }
        return { try { app.contentResolver.unregisterContentObserver(o) } catch (e: RuntimeException) { } }
    }

    /** Explicit broadcast to the companion package; no-op when absent. */
    fun requestRefresh(context: Context, nowMs: Long = System.currentTimeMillis()) {
        if (!refreshThrottle.tryAcquire(nowMs)) return
        try {
            context.sendBroadcast(Intent(WeatherContract.ACTION_REFRESH).setPackage(WeatherContract.PACKAGE))
        } catch (e: Exception) {
        }
    }

    /** The companion's settings screen (location/units), or null when it is not installed. */
    fun settingsIntent(context: Context): Intent? {
        val i = Intent(WeatherContract.ACTION_SETTINGS).setPackage(WeatherContract.PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { if (i.resolveActivity(context.packageManager) != null) i else null } catch (e: Exception) { null }
    }

    fun drawable(icon: WxIcon): Int = when (icon) {
        WxIcon.CLEAR_DAY -> R.drawable.ic_wx_clear_day
        WxIcon.CLEAR_NIGHT -> R.drawable.ic_wx_clear_night
        WxIcon.PARTLY_DAY -> R.drawable.ic_wx_partly_day
        WxIcon.PARTLY_NIGHT -> R.drawable.ic_wx_partly_night
        WxIcon.CLOUDY -> R.drawable.ic_wx_cloudy
        WxIcon.FOG -> R.drawable.ic_wx_fog
        WxIcon.DRIZZLE -> R.drawable.ic_wx_drizzle
        WxIcon.RAIN -> R.drawable.ic_wx_rain
        WxIcon.SNOW -> R.drawable.ic_wx_snow
        WxIcon.STORM -> R.drawable.ic_wx_storm
    }
}
