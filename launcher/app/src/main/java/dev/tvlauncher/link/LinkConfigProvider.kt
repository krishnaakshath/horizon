package dev.tvlauncher.link

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.setup.ConfigBackupIo

/**
 * M02 Link's encrypted phone backup reads and writes the launcher's configuration document here
 * (the same document as Settings > Backup, [ConfigBackupIo]). Exported for our own signed apps
 * only: the manifest's signature permission plus a check inside [call]. No rows to query.
 */
class LinkConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        if (c.checkCallingPermission(LinkContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) return null
        val out = Bundle()
        when (method) {
            LinkContract.METHOD_EXPORT_CONFIG -> ConfigBackupIo.exportText(c)?.let { out.putString(LinkContract.EXTRA_JSON, it) }
            LinkContract.METHOD_IMPORT_CONFIG -> {
                val json = extras?.getString(LinkContract.EXTRA_JSON)
                out.putBoolean(LinkContract.EXTRA_OK, json != null && ConfigBackupIo.restoreText(c, json))
            }
            else -> return null
        }
        return out
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
