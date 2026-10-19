package dev.tvlauncher.store.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.tvlauncher.store.core.InstallFallbackPolicy
import dev.tvlauncher.store.core.ReceiverTokenPolicy
import dev.tvlauncher.store.core.SessionJanitor
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
    @Volatile var tokenIssuedAt: Long = 0L
        private set
    @Volatile var currentSessionId: Int = -1
        private set

    /** Receives forwarded status broadcasts while the UI is alive (main thread). */
    interface Listener {
        /** Return true when the event was handled (e.g. the UI launched the confirmation dialog itself). */
        fun onInstallStatus(status: Int, message: String?, packageName: String?, confirm: Intent?): Boolean
    }

    @Volatile var listener: Listener? = null

    /** A session step failed; [stage] tells whether the legacy fallback may be tried. */
    class StageFailure(val stage: InstallFallbackPolicy.Stage, cause: Throwable) :
        Exception(cause.message ?: cause.javaClass.simpleName, cause)

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
     * Streams [apk] into a new session and commits it. Returns the session id. Throws [StageFailure] (the
     * session is abandoned). The caller deletes the file afterwards: the bytes are in the session by then.
     */
    fun commitSession(context: Context, apk: File, packageName: String, token: String): Int {
        val pi = context.packageManager.packageInstaller
        val id = try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)
            params.setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 26) params.setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            pi.createSession(params)
        } catch (e: Exception) {
            throw StageFailure(InstallFallbackPolicy.Stage.CREATE_SESSION, e)
        }
        var session: PackageInstaller.Session? = null
        var stage = InstallFallbackPolicy.Stage.WRITE
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
            stage = InstallFallbackPolicy.Stage.COMMIT
            currentToken = token
            tokenIssuedAt = System.currentTimeMillis()
            currentSessionId = id
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
            try { pi.abandonSession(id) } catch (_: Exception) {}
            clearToken()
            throw StageFailure(stage, e)
        }
    }

    /** After process death our sessions may linger: abandon every session we own except the active one. */
    fun abandonStaleSessions(context: Context) {
        try {
            val pi = context.packageManager.packageInstaller
            val views = pi.mySessions.map { SessionJanitor.SessionView(it.sessionId, it.installerPackageName) }
            for (id in SessionJanitor.toAbandon(views, context.packageName, currentSessionId.takeIf { it >= 0 })) {
                try { pi.abandonSession(id) } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        }
    }

    /** Gives up on an install whose confirmation never resolved: abandon the session and forget the token. */
    fun abandonCurrent(context: Context) {
        val id = currentSessionId
        clearToken()
        if (id >= 0) try { context.packageManager.packageInstaller.abandonSession(id) } catch (_: Exception) {}
    }

    enum class FallbackResult { STARTED, NO_SYSTEM_INSTALLER, FAILED }

    /**
     * Legacy path, used only when sessions are unsupported/forbidden (see [InstallFallbackPolicy]):
     * ACTION_INSTALL_PACKAGE sent EXPLICITLY to the system package installer (MATCH_SYSTEM_ONLY + FLAG_SYSTEM),
     * so no third-party app can receive the APK or the read grant. No system installer → don't install.
     */
    @Suppress("DEPRECATION")
    fun systemInstallerFallback(context: Context, apk: File): FallbackResult {
        return try {
            val uri = ApkShareProvider.uriFor(context, apk) ?: return FallbackResult.FAILED
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, ApkShareProvider.MIME_APK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val pm = context.packageManager
            val candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY).map {
                val ai = it.activityInfo.applicationInfo
                InstallFallbackPolicy.Candidate(it.activityInfo.packageName, (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            }
            val target = InstallFallbackPolicy.pickSystemInstaller(candidates) ?: return FallbackResult.NO_SYSTEM_INSTALLER
            context.startActivity(intent.setPackage(target))
            FallbackResult.STARTED
        } catch (_: Exception) {
            FallbackResult.FAILED
        }
    }

    internal fun clearToken() {
        currentToken = null
        currentSessionId = -1
    }
}

/** Not exported: only our own PendingIntent (filled in by the system installer) can reach it. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Installer.ACTION_STATUS) return
        val token = intent.getStringExtra(Installer.EXTRA_TOKEN)
        if (!ReceiverTokenPolicy.accepts(token, Installer.currentToken, Installer.tokenIssuedAt, System.currentTimeMillis())) return
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
