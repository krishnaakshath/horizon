package dev.tvlauncher.watch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Starts a Continue watching card: the program's own intent when it stays with the app that
 * published it ([WatchLaunchRule]), else that app's home screen. Every start is guarded.
 */
object WatchLauncher {
    /** True when something was started. */
    fun launch(context: Context, p: WatchProgram): Boolean {
        programIntent(p)?.let { if (start(context, it)) return true }
        return openApp(context, p.packageName)
    }

    fun programIntent(p: WatchProgram): Intent? {
        val raw = p.intentUri?.trim()
        if (!WatchLaunchRule.parseable(raw)) return null
        val i = try {
            if (raw!!.startsWith("intent:", ignoreCase = true) || raw.contains("#Intent;")) Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(raw))
        } catch (e: Exception) {
            return null
        }
        if (!WatchLaunchRule.allowed(p.packageName, i.`package`, i.component?.packageName)) return null
        if (i.component == null && i.`package` == null) i.setPackage(p.packageName)
        i.selector = null
        // Never pass on URI grants the row might ask for.
        i.flags = (i.flags and GRANT_FLAGS.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        return i
    }

    fun openApp(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val i = try {
            pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return false
        return start(context, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(context: Context, i: Intent): Boolean = try {
        context.startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
}
