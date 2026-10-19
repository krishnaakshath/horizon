package dev.tvlauncher.spotlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Android side of [HelperLink]: push the extra key, read the helper's state. */
object HelperBridge {
    fun push(context: Context, c: SpotlightConfig) {
        if (!isInstalled(context)) return
        val p = HelperLink.payload(c)
        try {
            val i = Intent(HelperLink.ACTION_SYNC)
                .setPackage(HelperLink.HELPER_PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(HelperLink.EXTRA_VERSION, HelperLink.VERSION)
                .putExtra(HelperLink.EXTRA_KEY, p.key)
                .putExtra(HelperLink.EXTRA_DOUBLE, p.double)
                .putExtra(HelperLink.EXTRA_WINDOW_MS, p.windowMs)
            context.sendBroadcast(i, HelperLink.PERMISSION)
        } catch (e: RuntimeException) {
            // Nothing to tell: the row keeps saying "Waiting for the helper".
        }
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(HelperLink.HELPER_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    fun serviceEnabled(context: Context): Boolean = try {
        HelperLink.serviceEnabled(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
    } catch (e: RuntimeException) {
        false
    }

    /** The helper's own screen, when it resolves (it is ours, but it may be disabled). */
    fun helperScreen(context: Context): Intent? {
        val i = Intent().setClassName(HelperLink.HELPER_PACKAGE, HelperLink.HELPER_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (context.packageManager.resolveActivity(i, 0) != null) i else null
        } catch (e: RuntimeException) {
            null
        }
    }
}

/**
 * The helper's state report ([HelperLink.ACTION_STATE]). Declared with the signature permission,
 * so only our own helper can send it; values are re-validated by [HelperLink.parseReport].
 */
class HelperStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HelperLink.ACTION_STATE) return
        try {
            val r = HelperLink.parseReport(
                intent.getIntExtra(HelperLink.EXTRA_VERSION, 0),
                intent.getStringExtra(HelperLink.EXTRA_REASON),
                intent.getBooleanExtra(HelperLink.EXTRA_SERVICE_ON, false),
                intent.getIntExtra(HelperLink.EXTRA_SYNCED_KEY, 0),
                intent.getBooleanExtra(HelperLink.EXTRA_SYNCED_DOUBLE, false),
                intent.getIntExtra(HelperLink.EXTRA_EFFECTIVE_KEY, 0)
            ) ?: return
            val prefs = SpotlightPrefs(context)
            prefs.saveReport(r)
            val c = prefs.load()
            if (HelperLink.needsPush(r, HelperLink.payload(c))) HelperBridge.push(context, c)
        } catch (e: RuntimeException) {
            // A malformed report changes nothing.
        }
    }
}
