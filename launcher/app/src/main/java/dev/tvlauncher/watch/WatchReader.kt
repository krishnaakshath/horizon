package dev.tvlauncher.watch

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build

/**
 * Reads the system TV provider's Watch Next list and preview channel programs. Every failure
 * (no provider, permission refused, a provider that throws) degrades to an empty list and a
 * [WatchAccess] the Settings page can explain. Never call on the main thread.
 *
 * The provider refuses a selection from apps without full access, so the query passes none and
 * everything is filtered here. Without full access it returns only rows other apps marked as
 * searchable (the default) plus the launcher's own, which is what the row needs.
 */
class WatchReader(context: Context) {
    private val app = context.applicationContext

    data class Result(val programs: List<WatchProgram>, val access: WatchAccess)

    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < 23 ||
        app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun providerPresent(): Boolean = try {
        app.packageManager.resolveContentProvider(AUTHORITY, 0) != null
    } catch (e: RuntimeException) {
        false
    }

    fun read(includePreview: Boolean): Result {
        val sdk = Build.VERSION.SDK_INT
        val present = sdk >= 26 && providerPresent()
        val granted = present && permissionGranted()
        if (!present || !granted) return Result(emptyList(), WatchAccess.resolve(sdk, present, granted, false, 0))
        val next = query(WATCH_NEXT_URI, WatchSource.WATCH_NEXT)
        val preview = if (includePreview && next != null) query(PREVIEW_URI, WatchSource.PREVIEW).orEmpty() else emptyList()
        val all = next.orEmpty() + preview
        return Result(all, WatchAccess.resolve(sdk, present, granted, next != null, all.size))
    }

    /** Null when the provider refused or failed; rows past [MAX_ROWS] are ignored. */
    private fun query(uri: Uri, source: WatchSource): List<WatchProgram>? = try {
        app.contentResolver.query(uri, null, null, null, null)?.use { c -> rows(c, source) }
    } catch (e: SecurityException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun rows(c: Cursor, source: WatchSource): List<WatchProgram> {
        val col = Columns(c)
        val out = ArrayList<WatchProgram>()
        while (out.size < MAX_ROWS && c.moveToNext()) {
            val pkg = col.str(c, "package_name") ?: continue
            val title = col.str(c, "title") ?: continue
            out += WatchProgram(
                id = col.long(c, "_id"),
                packageName = pkg,
                title = title,
                episodeTitle = col.str(c, "episode_title"),
                season = col.str(c, "season_display_number"),
                episode = col.str(c, "episode_display_number"),
                posterUri = col.str(c, "poster_art_uri") ?: col.str(c, "thumbnail_uri"),
                intentUri = col.str(c, "intent_uri"),
                positionMs = col.long(c, "last_playback_position_millis"),
                durationMs = col.long(c, "duration_millis"),
                lastEngagementMs = col.long(c, "last_engagement_time_utc_millis"),
                source = source,
                contentId = col.str(c, "content_id") ?: col.str(c, "internal_provider_id"),
                browsable = col.has("browsable").not() || col.long(c, "browsable") != 0L,
                weight = col.long(c, "weight").toInt()
            )
        }
        return out
    }

    /** Column lookups that tolerate a column this Android version does not have. */
    private class Columns(c: Cursor) {
        private val idx = c.columnNames.withIndex().associate { it.value to it.index }
        fun has(name: String) = name in idx
        fun str(c: Cursor, name: String): String? = idx[name]?.let { i ->
            try { if (c.isNull(i)) null else c.getString(i)?.trim()?.takeIf { it.isNotEmpty() } } catch (e: RuntimeException) { null }
        }
        fun long(c: Cursor, name: String): Long = idx[name]?.let { i ->
            try { if (c.isNull(i)) 0L else c.getLong(i) } catch (e: RuntimeException) { 0L }
        } ?: 0L
    }

    companion object {
        const val AUTHORITY = "android.media.tv"
        /** The TV provider's read permission for program information (a runtime permission). */
        const val PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA"
        val WATCH_NEXT_URI: Uri = Uri.parse("content://$AUTHORITY/watch_next_program")
        val PREVIEW_URI: Uri = Uri.parse("content://$AUTHORITY/preview_program")
        const val MAX_ROWS = 200
    }
}
