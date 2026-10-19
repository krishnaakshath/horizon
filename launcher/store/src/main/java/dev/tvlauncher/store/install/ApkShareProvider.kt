package dev.tvlauncher.store.install

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dev.tvlauncher.store.net.ApkDownloader
import java.io.File
import java.io.FileNotFoundException

/**
 * Minimal read-only provider for the legacy ACTION_INSTALL_PACKAGE fallback (no androidx dependency). Not exported;
 * access only through a per-intent URI grant. Serves nothing but "<uuid>.apk" files directly inside
 * cacheDir/downloads, verified by canonical path.
 */
class ApkShareProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun resolve(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val segs = uri.pathSegments
        if (uri.authority != authority(ctx) || segs.size != 1 || !NAME.matches(segs[0])) throw FileNotFoundException()
        val dir = ApkDownloader.downloadsDir(ctx).canonicalFile
        val f = File(dir, segs[0]).canonicalFile
        if (f.parentFile != dir || !f.isFile) throw FileNotFoundException()
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = MIME_APK

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = resolve(uri)
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        const val MIME_APK = "application/vnd.android.package-archive"
        private val NAME = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.apk$")

        fun authority(context: Context) = context.packageName + ".apk"

        fun uriFor(context: Context, file: File): Uri? {
            if (!NAME.matches(file.name)) return null
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath(file.name).build()
        }
    }
}
