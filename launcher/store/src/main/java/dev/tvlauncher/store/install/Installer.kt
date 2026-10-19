package dev.tvlauncher.store.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.FileInputStream

/**
 * Hands an APK to the SYSTEM installer. Never silent: a PackageInstaller session in MODE_FULL_INSTALL whose
 * commit always comes back as STATUS_PENDING_USER_ACTION for us (we are not a privileged installer, and on
 * API 31+ we additionally require user action), so Android shows its own confirmation dialog.
 */
object Installer {

    const val ACTION_STATUS = "dev.tvlauncher.store.INSTALL_STATUS"
    const val EXTRA_TOKEN = "dev.tvlauncher.store.TOKEN"

    /** One install in flight at a time; [token] ties status broadcasts to the request that started them. */
    @Volatile var currentToken: String? = null
        private set

    /** Receives forwarded status broadcasts while the UI is alive (main thread). */
    interface Listener {
        /** Return true when the event was handled (e.g. the UI launched the confirmation dialog itself). */
        fun onInstallStatus(status: Int, message: String?, packageName: String?, confirm: Intent?): Boolean
    }

    @Volatile var listener: Listener? = null

    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the per-app "Install unknown apps" screen for us; false when this TV has no such screen. */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Streams [apk] into a new session and commits it. Returns the session id. Throws on failure (the session
     * is abandoned). The caller deletes the file afterwards: the bytes are in the session by then.
     */
    fun commitSession(context: Context, apk: File, packageName: String, token: String): Int {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= 26) params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        val id = pi.createSession(params)
        var session: PackageInstaller.Session? = null
        try {
            session = pi.openSession(id)
            FileInputStream(apk).use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    session.fsync(out)
                }
            }
            currentToken = token
            val intent = Intent(context, InstallStatusReceiver::class.java)
                .setAction(ACTION_STATUS)
                .setPackage(context.packageName)
                .putExtra(EXTRA_TOKEN, token)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            // The system must be able to add the status extras, so the PendingIntent is mutable (explicit target).
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
            session.close()
            return id
        } catch (e: Exception) {
            try { session?.abandon() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            currentToken = null
            throw e
        }
    }

    /**
     * Fallback for builds where PackageInstaller sessions are unavailable: the classic system installer UI
     * via ACTION_VIEW and a read-only content URI from [ApkShareProvider] (cache/downloads only).
     */
    fun viewIntentFallback(context: Context, apk: File): Boolean {
        return try {
            val uri = ApkShareProvider.uriFor(context, apk) ?: return false
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, ApkShareProvider.MIME_APK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun clearToken() { currentToken = null }
}

/** Not exported: only our own PendingIntent (filled in by the system installer) can reach it. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Installer.ACTION_STATUS) return
        val token = intent.getStringExtra(Installer.EXTRA_TOKEN)
        if (token == null || token != Installer.currentToken) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val confirm: Intent? = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        } else null
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) Installer.clearToken()
        val handled = Installer.listener?.onInstallStatus(status, message, pkg, confirm) == true
        if (!handled && confirm != null) {
            try {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }
}
