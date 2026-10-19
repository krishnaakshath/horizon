package dev.tvlauncher.spotlight

/**
 * How the launcher and the Launcher Helper app share Spotlight's extra key.
 *
 * Mechanism: two explicit broadcasts guarded by one signature-level permission,
 * [PERMISSION], which both apps declare and use. Only apps signed with the same certificate as
 * ours can hold it, so:
 * - Launcher -> helper ([ACTION_SYNC], package [HELPER_PACKAGE]): sent with
 *   `sendBroadcast(intent, PERMISSION)`, so only a receiver holding the permission gets it; the
 *   helper's receiver is declared with `android:permission=PERMISSION`, so only a sender holding
 *   it is accepted. Payload: [EXTRA_KEY] (keycode or 0), [EXTRA_DOUBLE], [EXTRA_WINDOW_MS].
 * - Helper -> launcher ([ACTION_STATE], package "dev.tvlauncher"): the same guard both ways.
 *   Payload: whether its service is on, the key it holds from us and the key it acts on.
 *   [REASON_HELLO] (service just connected, or the helper's own screen changed something) asks
 *   the launcher to push again when the helper's copy is stale; [REASON_REPLY] (an answer to a
 *   push) never does, so the two apps cannot ping-pong.
 * Each side re-validates every value (keycode against the reserved list, window clamped) and
 * ignores a newer [VERSION] field it does not know. No ContentProvider, no world-readable
 * files; if the apps are signed differently nothing is delivered and the helper keeps the key
 * set on its own screen.
 */
object HelperLink {
    const val PERMISSION = "dev.tvlauncher.permission.SPOTLIGHT_SYNC"
    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
    const val HELPER_SERVICE = "dev.tvlauncher.helper.HelperService"
    const val HELPER_ACTIVITY = "dev.tvlauncher.helper.HelperSettingsActivity"
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

    /** What the helper needs: the extra key and whether it opens on a double-press. */
    data class Payload(val key: Int, val double: Boolean, val windowMs: Long = TriggerDetector.DOUBLE_MS)

    fun payload(c: SpotlightConfig): Payload {
        val key = c.extraKey.takeIf { TriggerKeys.check(it) == TriggerKeys.Check.OK } ?: 0
        return Payload(key, key != 0 && c.doublePress == DoublePress.EXTRA)
    }

    /** The helper's last report, as stored by the launcher. */
    data class Report(
        val serviceOn: Boolean,
        val syncedKey: Int,
        val syncedDouble: Boolean,
        val effectiveKey: Int,
        val hello: Boolean
    )

    /** Validates a received report; null when it cannot be one of ours. */
    fun parseReport(
        version: Int,
        reason: String?,
        serviceOn: Boolean,
        syncedKey: Int,
        syncedDouble: Boolean,
        effectiveKey: Int
    ): Report? {
        if (version < 1) return null
        if (reason != REASON_HELLO && reason != REASON_REPLY) return null
        fun clean(k: Int) = if (TriggerKeys.check(k) == TriggerKeys.Check.RESERVED) 0 else k
        val synced = clean(syncedKey)
        return Report(serviceOn, synced, syncedDouble && synced != 0, clean(effectiveKey), reason == REASON_HELLO)
    }

    /** Push again only when the helper said hello with a copy that differs from ours. */
    fun needsPush(r: Report, p: Payload): Boolean = r.hello && (r.syncedKey != p.key || r.syncedDouble != p.double)

    /** The helper holds exactly our current extra key. */
    fun inSync(r: Report?, p: Payload): Boolean = r != null && r.syncedKey == p.key && r.syncedDouble == p.double

    /**
     * Whether Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES ([flat], ':'-separated component
     * names, short or full class form) lists the helper's service.
     */
    fun serviceEnabled(flat: String?): Boolean {
        if (flat.isNullOrBlank()) return false
        return flat.split(':').any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2 || parts[0] != HELPER_PACKAGE) return@any false
            val cls = parts[1]
            cls == HELPER_SERVICE || (cls.startsWith(".") && HELPER_PACKAGE + cls == HELPER_SERVICE)
        }
    }
}

/**
 * The "Open from any app" row: never a link no app can handle, never developer text.
 */
object HelperStatus {
    enum class Action { NONE, OPEN_HELPER, OPEN_KEYS }

    data class Display(val value: String, val subtitle: String?, val action: Action)

    const val FINISH_SETUP = "Finish setup to enable"

    fun resolve(
        installed: Boolean,
        serviceOn: Boolean,
        helperScreen: Boolean,
        payload: HelperLink.Payload,
        report: HelperLink.Report?,
        keyName: (Int) -> String
    ): Display = when {
        !installed -> Display("Not installed", "Add Launcher Helper to open Spotlight from other apps", Action.NONE)
        !serviceOn -> Display(FINISH_SETUP, null, if (helperScreen) Action.OPEN_HELPER else Action.NONE)
        payload.key == 0 -> Display("Choose a key", "Pick an extra key under Keys that open Spotlight", Action.OPEN_KEYS)
        HelperLink.inSync(report, payload) -> {
            val name = keyName(payload.key)
            Display("On", if (payload.double) "Press $name twice in any app" else "Press $name in any app", Action.OPEN_KEYS)
        }
        else -> Display("Waiting for the helper", "It picks up ${keyName(payload.key)} when it next starts", Action.NONE)
    }
}
