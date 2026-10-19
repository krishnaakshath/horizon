package dev.tvlauncher.weather

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Read-only provider exposing the last good snapshot (possibly stale; consumers check updated_ms). */
class WeatherProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(WeatherContract.AUTHORITY, WeatherContract.PATH_CURRENT, CURRENT)
        addURI(WeatherContract.AUTHORITY, WeatherContract.PATH_DAILY, DAILY)
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CURRENT -> "vnd.android.cursor.item/vnd.dev.tvlauncher.weather.current"
        DAILY -> "vnd.android.cursor.dir/vnd.dev.tvlauncher.weather.daily"
        else -> null
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        val which = matcher.match(uri)
        return try {
            val ctx = context ?: return MatrixCursor(WeatherContract.CURRENT_COLUMNS)
            val state = WeatherStore(ctx).load()
            val snap = state.snapshot
            val cursor = when (which) {
                CURRENT -> MatrixCursor(WeatherContract.CURRENT_COLUMNS).also { c ->
                    if (snap != null && state.place != null) {
                        c.addRow(arrayOf<Any>(
                            state.place.name, snap.tempC, snap.feelsC, snap.code,
                            WmoCodes.text(snap.code), if (snap.isDay) 1 else 0, snap.highC, snap.lowC,
                            snap.humidity, snap.windKmh, snap.updatedMs, state.unit,
                        ))
                    }
                }
                DAILY -> MatrixCursor(WeatherContract.DAILY_COLUMNS).also { c ->
                    snap?.daily?.sortedBy { it.dayMs }?.take(5)?.forEach {
                        c.addRow(arrayOf<Any>(it.dayMs, it.code, WmoCodes.text(it.code), it.highC, it.lowC))
                    }
                }
                else -> MatrixCursor(WeatherContract.CURRENT_COLUMNS)
            }
            cursor.setNotificationUri(ctx.contentResolver, uri)
            cursor
        } catch (e: Exception) {
            MatrixCursor(if (which == DAILY) WeatherContract.DAILY_COLUMNS else WeatherContract.CURRENT_COLUMNS)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    private companion object {
        const val CURRENT = 1
        const val DAILY = 2
    }
}
