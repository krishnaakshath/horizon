package dev.tvlauncher.controllers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import dev.tvlauncher.input.GamepadSettingsSource

/**
 * Read-only: the controller settings (one row, one column, [dev.tvlauncher.input.GamepadSettings]
 * text form) for the companion apps and the helper, so A/B swap, stick sensitivity and the Guide
 * button behave the same everywhere. Nothing private is in it; nothing can be written through it.
 */
class GamepadSettingsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val ctx = context ?: return null
        val c = MatrixCursor(arrayOf(GamepadSettingsSource.COLUMN), 1)
        c.addRow(arrayOf<Any>(GamepadSettingsSource.get(ctx).current().encode()))
        c.setNotificationUri(ctx.contentResolver, GamepadSettingsSource.URI)
        return c
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.dev.tvlauncher.gamepad"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
