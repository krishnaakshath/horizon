package dev.tvlauncher.link.update

import android.content.Context
import dev.tvlauncher.link.LinkStore
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.net.SafeHttp

/** Runs [UpdateCheck] against the network; only when the owner enabled it and set a repository. */
object Updates {
    /** True when a check ran and succeeded. Blocking. */
    fun check(context: Context, force: Boolean): Boolean {
        val store = LinkStore(context)
        if (!store.updateEnabled) return false
        val url = UpdateCheck.latestUrl(store.updateRepo) ?: return false
        val now = System.currentTimeMillis()
        if (!UpdateCheck.due(store.updateCheckedMs, now, force)) return true
        return try {
            val r = SafeHttp.request("GET", url, mapOf("Accept" to "application/vnd.github+json"), maxBytes = UpdateCheck.MAX_BYTES)
            if (r.status != 200) return false
            val release = UpdateCheck.parse(r.text()) ?: return false
            store.updateLatest = release.tag
            store.updateAvailable = UpdateCheck.available(release, installedVersion(context))
            store.updateSha = release.apk?.sha256 != null
            store.updateCheckedMs = now
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(LinkContract.LAUNCHER_PACKAGE, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
