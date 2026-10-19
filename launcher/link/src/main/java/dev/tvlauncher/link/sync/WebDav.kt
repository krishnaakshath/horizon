package dev.tvlauncher.link.sync

import dev.tvlauncher.link.net.SafeHttp
import dev.tvlauncher.link.net.UrlPolicy
import dev.tvlauncher.link.util.B64

/**
 * Upload and download of the encrypted backup to a WebDAV (or any HTTP PUT/GET) location the
 * owner typed in. Runs only when the owner presses "Back up now" / "Restore now"; nothing is
 * ever sent on a schedule. The file is already encrypted, and plain http is still refused for
 * anything outside the home network ([UrlPolicy]).
 */
object WebDav {
    /** The file URL: a folder URL ("…/backups/") gets [BackupZip.FILE_NAME] appended. Null if unusable. */
    fun fileUrl(input: String?): String? {
        val s = input?.trim() ?: return null
        val p = UrlPolicy.parse(s) ?: return null
        if (s.contains('?')) return null
        return if (s.endsWith("/") || p.path.isEmpty()) s.trimEnd('/') + "/" + BackupZip.FILE_NAME else s
    }

    fun basicAuth(user: String, password: String): String? {
        if (user.isEmpty()) return null
        if (user.contains(':') || (user + password).any { it.isISOControl() }) return null
        return "Basic " + B64.encode("$user:$password".toByteArray(Charsets.UTF_8))
    }

    fun interface Transport {
        fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?): SafeHttp.Response
    }

    private val defaultTransport = Transport { m, u, h, b -> SafeHttp.request(m, u, h, b, maxBytes = BackupZip.MAX_FILE, readMs = 15000) }

    /** True on 200/201/204. */
    fun upload(url: String, auth: String?, file: ByteArray, t: Transport = defaultTransport): Boolean {
        val h = linkedMapOf("Content-Type" to "application/octet-stream")
        auth?.let { h["Authorization"] = it }
        return t.send("PUT", url, h, file).status in setOf(200, 201, 204)
    }

    /** The file, or null when the server does not return one. */
    fun download(url: String, auth: String?, t: Transport = defaultTransport): ByteArray? {
        val h = LinkedHashMap<String, String>()
        auth?.let { h["Authorization"] = it }
        val r = t.send("GET", url, h, null)
        return if (r.status == 200 && r.body.isNotEmpty()) r.body else null
    }
}
