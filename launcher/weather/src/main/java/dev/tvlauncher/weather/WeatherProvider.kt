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
        addURI(WeatherContract.AUTHORITY, WeatherContract.PATH_SUN, SUN)
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CURRENT -> "vnd.android.cursor.item/vnd.dev.tvlauncher.weather.current"
        DAILY -> "vnd.android.cursor.dir/vnd.dev.tvlauncher.weather.daily"
        SUN -> "vnd.android.cursor.dir/vnd.dev.tvlauncher.weather.sun"
        else -> null
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        val which = matcher.match(uri)
        return try {
            val ctx = context ?: return MatrixCursor(columnsFor(which))
            val state = WeatherStore(ctx).load()
            val cursor = MatrixCursor(columnsFor(which))
            val rows = when (which) {
                CURRENT -> ProviderRows.current(state)
                DAILY -> ProviderRows.daily(state)
                SUN -> ProviderRows.sun(state)
                else -> emptyList()
            }
            rows.forEach { cursor.addRow(it) }
            cursor.setNotificationUri(ctx.contentResolver, uri)
            cursor
        } catch (e: Exception) {
            MatrixCursor(columnsFor(which))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    private fun columnsFor(which: Int): Array<String> = when (which) {
        DAILY -> WeatherContract.DAILY_COLUMNS
        SUN -> WeatherContract.SUN_COLUMNS
        else -> WeatherContract.CURRENT_COLUMNS
    }

    private companion object {
        const val CURRENT = 1
        const val DAILY = 2
        const val SUN = 3
    }
}
