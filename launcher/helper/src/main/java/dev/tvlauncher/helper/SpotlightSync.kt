package dev.tvlauncher.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The helper's half of the launcher link (the launcher documents it in spotlight/HelperLink.kt).
 *
 * The launcher pushes its Spotlight extra key with [ACTION_SYNC]; the helper answers, and says
 * hello when its service connects, with [ACTION_STATE]. Both broadcasts are explicit (package
 * set) and guarded by the signature permission [PERMISSION] that both apps declare: the
 * receiver requires the sender to hold it and the sender requires the receiver to hold it, so
 * only apps signed with our certificate take part. Every received value is validated again
 * here ([accept]).
 */
object SpotlightSync {
    const val PERMISSION = "dev.tvlauncher.permission.SPOTLIGHT_SYNC"
    const val LAUNCHER_PACKAGE = "dev.tvlauncher"
    const val ACTION_SYNC = "dev.tvlauncher.helper.action.SPOTLIGHT_SYNC"
    const val ACTION_STATE = "dev.tvlauncher.action.HELPER_STATE"

    const val VERSION = 1
    const val EXTRA_VERSION = "v"
    const val EXTRA_KEY = "key"
    const val EXTRA_DOUBLE = "double"
    const val EXTRA_WINDOW_MS = "windowMs"
    const val EXTRA_REASON = "reason"
    const val EXTRA_SERVICE_ON = "serviceOn"
    const val EXTRA_SYNCED_KEY = "syncedKey"
    const val EXTRA_SYNCED_DOUBLE = "syncedDouble"
    const val EXTRA_EFFECTIVE_KEY = "effectiveKey"
    const val REASON_HELLO = "hello"
    const val REASON_REPLY = "reply"

    const val DEFAULT_WINDOW_MS = 450L
    const val MIN_WINDOW_MS = 250L
    const val MAX_WINDOW_MS = 800L

    fun clampWindow(ms: Long): Long = ms.coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)

    /**
     * Settings after a push. Version 0 (missing) is ignored; a reserved key clears the
     * launcher key (the helper's own key is untouched either way).
     */
    fun accept(s: HelperSettings, version: Int, key: Int, double: Boolean, windowMs: Long): HelperSettings {
        if (version < 1) return s
        val k = key.takeIf { ShortcutKeys.isAllowed(it) } ?: 0
        return s.copy(launcherKey = k, launcherDouble = k != 0 && double, launcherWindowMs = clampWindow(windowMs))
    }

    /** Whether ENABLED_ACCESSIBILITY_SERVICES lists [pkg]/[cls] (full or short class form). */
    fun serviceListed(flat: String?, pkg: String, cls: String): Boolean {
        if (flat.isNullOrBlank()) return false
        return flat.split(':').any { e ->
            val p = e.trim().split('/')
            p.size == 2 && p[0] == pkg && (p[1] == cls || (p[1].startsWith(".") && pkg + p[1] == cls))
        }
    }

    fun serviceOn(context: Context): Boolean = try {
        serviceListed(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            context.packageName, HelperService::class.java.name
        )
    } catch (e: RuntimeException) {
        false
    }

    /** Tells the launcher what the helper holds. [hello] asks it to push again if ours is stale. */
    fun sendState(context: Context, hello: Boolean, serviceOn: Boolean = serviceOn(context)) {
        try {
            val s = HelperPrefs(context).load()
            val i = Intent(ACTION_STATE)
                .setPackage(LAUNCHER_PACKAGE)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(EXTRA_VERSION, VERSION)
                .putExtra(EXTRA_REASON, if (hello) REASON_HELLO else REASON_REPLY)
                .putExtra(EXTRA_SERVICE_ON, serviceOn)
                .putExtra(EXTRA_SYNCED_KEY, s.launcherKey)
                .putExtra(EXTRA_SYNCED_DOUBLE, s.launcherDouble)
                .putExtra(EXTRA_EFFECTIVE_KEY, s.effectiveKey)
            context.sendBroadcast(i, PERMISSION)
        } catch (e: RuntimeException) {
            // The launcher's row just stays as it was.
        }
    }
}

/** Receives the launcher's Spotlight key ([SpotlightSync.ACTION_SYNC]); guarded by the signature permission. */
class SpotlightSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SpotlightSync.ACTION_SYNC) return
        try {
            HelperPrefs(context).update {
                SpotlightSync.accept(
                    it,
                    intent.getIntExtra(SpotlightSync.EXTRA_VERSION, 0),
                    intent.getIntExtra(SpotlightSync.EXTRA_KEY, 0),
                    intent.getBooleanExtra(SpotlightSync.EXTRA_DOUBLE, false),
                    intent.getLongExtra(SpotlightSync.EXTRA_WINDOW_MS, SpotlightSync.DEFAULT_WINDOW_MS)
                )
            }
            SpotlightSync.sendState(context, hello = false)
        } catch (e: RuntimeException) {
            // Keep the previous key.
        }
    }
}
