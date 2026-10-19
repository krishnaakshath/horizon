package dev.tvlauncher.audit

/** Pure decisions for [AuditReceiver] (debug builds only). */
object AuditCommands {
    const val AUDIT = "dev.tvlauncher.debug.AUDIT"
    const val DUMP_TREE = "dev.tvlauncher.debug.AUDIT_DUMP_TREE"
    const val EXTRA_LABEL = "label"
    const val EXTRA_NONCE = "nonce"
    /** Boolean; false turns off the 18 sp TV body-text check (on by default for the TV). */
    const val EXTRA_BODY_TEXT = "bodyText"
    /** Files kept per audit directory (oldest pruned; latest*.json never). */
    const val KEEP_FILES = 20
    const val LOG_TAG = "LayoutAudit"
    const val DEFAULT_LABEL = "audit"

    enum class Mode { AUDIT, DUMP_TREE }

    data class Command(val mode: Mode, val label: String, val nonce: String?, val bodyText: Boolean = true)

    private val LABEL_CHAR = Regex("[^A-Za-z0-9_.-]")
    private val NONCE = Regex("[A-Za-z0-9]{1,16}")

    /** Keeps only [A-Za-z0-9_.-], at most 40 chars; anything unusable becomes [DEFAULT_LABEL]. */
    fun sanitizeLabel(raw: Any?): String {
        val s = (raw as? String)?.replace(LABEL_CHAR, "")?.take(40).orEmpty()
        return if (s.isEmpty() || s.all { it == '.' }) DEFAULT_LABEL else s
    }

    /** A nonce is echoed into logcat so a driver can match its own request; malformed means none. */
    fun sanitizeNonce(raw: Any?): String? = (raw as? String)?.takeIf { NONCE.matches(it) }

    /** Only a real Boolean counts; anything else means the default (on). */
    fun parseBodyText(raw: Any?): Boolean = raw as? Boolean ?: true

    /** The engine config the receiver uses on the TV. */
    fun config(cmd: Command): AuditConfig = AuditConfig(reportBelowBodyText = cmd.bodyText)

    /** Names of dated audit files to delete so that the newest [keep] (by epoch in the name) remain; latest*.json is never pruned. */
    fun prune(names: List<String>, keep: Int = KEEP_FILES): List<String> {
        val epoch = Regex("-(\\d{10,})(-tree)?\\.json$")
        val dated = names.filter { !it.startsWith("latest") }.mapNotNull { n -> epoch.find(n)?.let { n to it.groupValues[1].toLong() } }
        return dated.sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).drop(keep).map { it.first }.sorted()
    }

    fun decide(action: String?, debuggable: Boolean, rawLabel: Any?, rawNonce: Any?, rawBodyText: Any? = null): Command? {
        if (!debuggable) return null
        val mode = when (action) {
            AUDIT -> Mode.AUDIT
            DUMP_TREE -> Mode.DUMP_TREE
            else -> return null
        }
        return Command(mode, sanitizeLabel(rawLabel), sanitizeNonce(rawNonce), parseBodyText(rawBodyText))
    }

    fun fileName(cmd: Command, epochMs: Long): String =
        if (cmd.mode == Mode.AUDIT) "${cmd.label}-$epochMs.json" else "${cmd.label}-$epochMs-tree.json"

    fun latestName(cmd: Command): String = if (cmd.mode == Mode.AUDIT) "latest.json" else "latest-tree.json"

    /** The single logcat line a driver waits for. Values never contain spaces (path aside, which is app-owned). */
    fun logLine(cmd: Command, findings: Int, high: Int, nodes: Int, path: String?, error: String?, internal: String? = null): String {
        val sb = StringBuilder()
        if (cmd.mode == Mode.AUDIT) sb.append("LAYOUT_AUDIT label=${cmd.label} findings=$findings high=$high")
        else sb.append("LAYOUT_AUDIT_TREE label=${cmd.label} nodes=$nodes")
        sb.append(" file=").append(path ?: "-")
        if (internal != null) sb.append(" internal=").append(internal)
        if (cmd.nonce != null) sb.append(" nonce=").append(cmd.nonce)
        if (error != null) sb.append(" error=").append(error.replace(Regex("\\s+"), "_").take(80))
        return sb.toString()
    }
}
