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
 * FLAG_DEBUGGABLE check; the only extras read are `label` (sanitized to [A-Za-z0-9_.-]{1,40}) and
 * `nonce` ([A-Za-z0-9]{1,16}, echoed into logcat), both defensively.
 *
 * ```
 * adb shell am broadcast -a dev.tvlauncher.debug.AUDIT -n dev.tvlauncher/dev.tvlauncher.audit.AuditReceiver --es label home-1
 * adb shell am broadcast -a dev.tvlauncher.debug.AUDIT_DUMP_TREE -n dev.tvlauncher/dev.tvlauncher.audit.AuditReceiver --es label home-1
 * adb logcat -d -s LayoutAudit
 * ```
 * Output: `<externalFilesDir>/audit/<label>-<epochMs>.json` (+ `latest.json`), one logcat line
 * `LAYOUT_AUDIT label=<label> findings=<N> high=<H> file=<path> [nonce=<n>] [error=<e>]`.
 */
class AuditReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val action = intent?.action
        if (!debuggable || AuditCommands.decide(action, true, null, null) == null) return
        val label = try { @Suppress("DEPRECATION") intent?.extras?.get(AuditCommands.EXTRA_LABEL) } catch (t: Throwable) { null }
        val nonce = try { @Suppress("DEPRECATION") intent?.extras?.get(AuditCommands.EXTRA_NONCE) } catch (t: Throwable) { null }
        val cmd = AuditCommands.decide(action, debuggable, label, nonce) ?: return
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
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "audit").apply { mkdirs() }
        val nodes = snap?.nodes.orEmpty()
        val meta = linkedMapOf<String, Any?>(
            "label" to cmd.label, "epochMs" to now, "activity" to activity,
            "screenW" to snap?.screenW, "screenH" to snap?.screenH, "density" to snap?.density,
            "windows" to snap?.windows, "nodes" to nodes.size, "notes" to snap?.notes.orEmpty(), "error" to error,
        )
        val file = File(dir, AuditCommands.fileName(cmd, now))
        val log: String
        if (cmd.mode == AuditCommands.Mode.AUDIT) {
            val config = AuditConfig()
            val result = if (snap == null) AuditResult(emptyList(), 0, emptyMap(), emptyMap())
                else AuditEngine.run(nodes, snap.screenW, snap.screenH, snap.density, config)
            meta["config"] = config.toString()
            file.writeText(AuditReport.toJson(result, meta))
            log = AuditCommands.logLine(cmd, result.total, result.bySeverity[Severity.HIGH] ?: 0, nodes.size, file.absolutePath, error)
            Log.i(AuditCommands.LOG_TAG, "LAYOUT_AUDIT_SUMMARY label=${cmd.label} ${AuditReport.summary(result)}")
        } else {
            file.writeText(AuditReport.nodesToJson(nodes, meta))
            log = AuditCommands.logLine(cmd, 0, 0, nodes.size, file.absolutePath, error)
        }
        file.copyTo(File(dir, AuditCommands.latestName(cmd)), overwrite = true)
        Log.i(AuditCommands.LOG_TAG, log)
    }
}
