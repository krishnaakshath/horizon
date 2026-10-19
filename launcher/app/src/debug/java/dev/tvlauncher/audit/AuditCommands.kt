package dev.tvlauncher.audit

/** Pure decisions for [AuditReceiver] (debug builds only). */
object AuditCommands {
    const val AUDIT = "dev.tvlauncher.debug.AUDIT"
    const val DUMP_TREE = "dev.tvlauncher.debug.AUDIT_DUMP_TREE"
    const val EXTRA_LABEL = "label"
    const val EXTRA_NONCE = "nonce"
    const val LOG_TAG = "LayoutAudit"
    const val DEFAULT_LABEL = "audit"

    enum class Mode { AUDIT, DUMP_TREE }

    data class Command(val mode: Mode, val label: String, val nonce: String?)

    private val LABEL_CHAR = Regex("[^A-Za-z0-9_.-]")
    private val NONCE = Regex("[A-Za-z0-9]{1,16}")

    /** Keeps only [A-Za-z0-9_.-], at most 40 chars; anything unusable becomes [DEFAULT_LABEL]. */
    fun sanitizeLabel(raw: Any?): String {
        val s = (raw as? String)?.replace(LABEL_CHAR, "")?.take(40).orEmpty()
        return if (s.isEmpty() || s.all { it == '.' }) DEFAULT_LABEL else s
    }

    /** A nonce is echoed into logcat so a driver can match its own request; malformed means none. */
    fun sanitizeNonce(raw: Any?): String? = (raw as? String)?.takeIf { NONCE.matches(it) }

    fun decide(action: String?, debuggable: Boolean, rawLabel: Any?, rawNonce: Any?): Command? {
        if (!debuggable) return null
        val mode = when (action) {
            AUDIT -> Mode.AUDIT
            DUMP_TREE -> Mode.DUMP_TREE
            else -> return null
        }
        return Command(mode, sanitizeLabel(rawLabel), sanitizeNonce(rawNonce))
    }

    fun fileName(cmd: Command, epochMs: Long): String =
        if (cmd.mode == Mode.AUDIT) "${cmd.label}-$epochMs.json" else "${cmd.label}-$epochMs-tree.json"

    fun latestName(cmd: Command): String = if (cmd.mode == Mode.AUDIT) "latest.json" else "latest-tree.json"

    /** The single logcat line a driver waits for. Values never contain spaces (path aside, which is app-owned). */
    fun logLine(cmd: Command, findings: Int, high: Int, nodes: Int, path: String?, error: String?): String {
        val sb = StringBuilder()
        if (cmd.mode == Mode.AUDIT) sb.append("LAYOUT_AUDIT label=${cmd.label} findings=$findings high=$high")
        else sb.append("LAYOUT_AUDIT_TREE label=${cmd.label} nodes=$nodes")
        sb.append(" file=").append(path ?: "-")
        if (cmd.nonce != null) sb.append(" nonce=").append(cmd.nonce)
        if (error != null) sb.append(" error=").append(error.replace(Regex("\\s+"), "_").take(80))
        return sb.toString()
    }
}
