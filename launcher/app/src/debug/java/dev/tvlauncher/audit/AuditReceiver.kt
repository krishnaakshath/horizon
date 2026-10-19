package dev.tvlauncher.audit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Debug-build-only layout audit trigger. Lives in src/debug with its manifest entry, so release builds
 * contain neither. Protected by android.permission.DUMP (adb shell has it, apps do not) and a second
 * FLAG_DEBUGGABLE check; the only extras read are `label` (sanitized to [A-Za-z0-9_.-]{1,40}),
 * `nonce` ([A-Za-z0-9]{1,16}, echoed into logcat) and `bodyText` (boolean, default true: report text
 * below the 18 sp TV body size as LOW), all defensively.
 *
 * ```
 * adb shell am broadcast -a dev.tvlauncher.debug.AUDIT -n dev.tvlauncher/dev.tvlauncher.audit.AuditReceiver --es label home-1
 * adb shell am broadcast -a dev.tvlauncher.debug.AUDIT_DUMP_TREE -n dev.tvlauncher/dev.tvlauncher.audit.AuditReceiver --es label home-1
 * adb logcat -d -s LayoutAudit
 * ```
 * Output: `<externalFilesDir>/audit/<label>-<epochMs>.json` AND `<filesDir>/audit/<same name>` (+ `latest.json`
 * in both; the newest 20 dated files are kept per directory), and one logcat line
 * `LAYOUT_AUDIT label=<label> findings=<N> high=<H> file=<path> internal=<path> [nonce=<n>] [error=<e>]`.
 * `file` is the external copy (adb pull); `internal` is readable with `run-as <pkg> cat files/audit/<name>`.
 */
class AuditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val action = intent?.action
        if (!debuggable || AuditCommands.decide(action, true, null, null) == null) return
        val label = try { @Suppress("DEPRECATION") intent?.extras?.get(AuditCommands.EXTRA_LABEL) } catch (t: Throwable) { null }
        val nonce = try { @Suppress("DEPRECATION") intent?.extras?.get(AuditCommands.EXTRA_NONCE) } catch (t: Throwable) { null }
        val body = try { @Suppress("DEPRECATION") intent?.extras?.get(AuditCommands.EXTRA_BODY_TEXT) } catch (t: Throwable) { null }
        val cmd = AuditCommands.decide(action, debuggable, label, nonce, body) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        Handler(Looper.getMainLooper()).post {
            // Main thread: snapshot only. Engine + IO run on a worker.
            var snap: ViewTreeSnapshotter.Snapshot? = null
            var activityName: String? = null
            var error: String? = null
            try {
                val act = AuditTracker.current()
                if (act == null) error = "no_resumed_activity" else {
                    activityName = act.javaClass.name
                    snap = ViewTreeSnapshotter.snapshotActivity(act)
                }
            } catch (t: Throwable) {
                error = "snapshot_failed:" + t.javaClass.simpleName
            }
            Thread({
                try { finish(app, cmd, snap, activityName, error) }
                catch (t: Throwable) { Log.i(AuditCommands.LOG_TAG, AuditCommands.logLine(cmd, 0, 0, 0, null, "write_failed:" + t.javaClass.simpleName)) }
                finally { pending.finish() }
            }, "layout-audit").start()
        }
    }

    private fun finish(context: Context, cmd: AuditCommands.Command, snap: ViewTreeSnapshotter.Snapshot?, activity: String?, error: String?) {
        val now = System.currentTimeMillis()
        val internalDir = File(context.filesDir, "audit").apply { mkdirs() }
        val externalDir = try { context.getExternalFilesDir(null)?.let { File(it, "audit").apply { mkdirs() } } } catch (t: Throwable) { null }
        val nodes = snap?.nodes.orEmpty()
        val meta = linkedMapOf<String, Any?>(
            "label" to cmd.label, "epochMs" to now, "activity" to activity,
            "screenW" to snap?.screenW, "screenH" to snap?.screenH, "density" to snap?.density,
            "windows" to snap?.windows, "nodes" to nodes.size, "notes" to snap?.notes.orEmpty(), "error" to error,
        )
        val name = AuditCommands.fileName(cmd, now)
        val json: String
        var findings = 0
        var high = 0
        if (cmd.mode == AuditCommands.Mode.AUDIT) {
            val config = AuditCommands.config(cmd)
            val result = if (snap == null) AuditResult(emptyList(), 0, emptyMap(), emptyMap())
                else AuditEngine.run(nodes, snap.screenW, snap.screenH, snap.density, config)
            meta["config"] = config.toString()
            meta["ignoredNodes"] = result.stats["ignoredNodes"]
            meta["layerNodes"] = result.stats["layerNodes"]
            json = AuditReport.toJson(result, meta)
            findings = result.total
            high = result.bySeverity[Severity.HIGH] ?: 0
            Log.i(AuditCommands.LOG_TAG, "LAYOUT_AUDIT_SUMMARY label=${cmd.label} ${AuditReport.summary(result)}")
            for (n in result.notes) Log.i(AuditCommands.LOG_TAG, "LAYOUT_AUDIT_NOTE label=${cmd.label} $n")
        } else {
            json = AuditReport.nodesToJson(nodes, meta)
        }
        val internal = write(internalDir, name, cmd, json)
        val external = externalDir?.let { try { write(it, name, cmd, json) } catch (t: Throwable) { null } }
        val primary = external ?: internal
        Log.i(AuditCommands.LOG_TAG, AuditCommands.logLine(cmd, findings, high, nodes.size, primary.absolutePath, error, internal.absolutePath))
    }

    /** Writes `<dir>/<name>` and `<dir>/latest*.json`, then prunes old dated files in [dir]. */
    private fun write(dir: File, name: String, cmd: AuditCommands.Command, json: String): File {
        val f = File(dir, name)
        f.writeText(json)
        File(dir, AuditCommands.latestName(cmd)).writeText(json)
        val names = dir.list()?.toList().orEmpty()
        for (old in AuditCommands.prune(names)) try { File(dir, old).delete() } catch (t: Throwable) {}
        return f
    }
}
